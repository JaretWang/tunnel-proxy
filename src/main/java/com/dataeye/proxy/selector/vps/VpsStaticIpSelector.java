package com.dataeye.proxy.selector.vps;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.extra.ssh.JschUtil;
import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.config.VpsConfig;
import com.dataeye.proxy.cons.HttpCons;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.service.EnterpriseWeChatRobotSerice;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.VpsInstanceService;
import com.dataeye.proxy.service.impl.VpsFetchServiceImpl;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import com.jcraft.jsch.Session;
import lombok.Getter;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/11/15 18:34
 * @description
 */
@Component
public class VpsStaticIpSelector implements CommonIpSelector, DisposableBean {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("VpsStaticIpSelector");
    private final ScheduledExecutorService ipPoolSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("ipPoolSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService healthCheckSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("healthCheckSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService checkAllVpsSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("checkAllVpsSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final CopyOnWriteArrayList<VpsInstance> errorInstance = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<VpsInstance> replayVpsQueue = new ConcurrentLinkedQueue<>();
    @Getter
    private final ConcurrentHashMap<VpsInstance, AtomicInteger> vpsAlarmCache = new ConcurrentHashMap<>();
    @Autowired
    VpsConfig vpsConfig;
    private final ConcurrentLinkedQueue<ProxyIp> ipPool = new ConcurrentLinkedQueue<>();
    @Autowired
    VpsInstanceService vpsInstanceService;
    @Getter
    List<VpsInstance> vpsInstances;
    @Autowired
    VpsFetchServiceImpl vpsFetchService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Resource
    TunnelInitService tunnelInitService;
    @Resource(name = "replayVps")
    ThreadPoolTaskExecutor replayVpsThreadPool;
    @Resource(name = "ipAliveCheck")
    ThreadPoolTaskExecutor ipAliveCheckThreadPool;
    @Autowired
    EnterpriseWeChatRobotSerice enterpriseWeChatRobotSerice;

    @Override
    public void init() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
            return;
        }
        // 初始化ip池
        updateIpPool();
        if (ipPool.isEmpty()) {
            throw new RuntimeException("初始化ip池失败");
        }
        replayVpsThreadPool.submit(this::replayVps2);
        // 检查所有vps存活，并发送告警邮件
        checkAllVpsSchedule.scheduleAtFixedRate(this::checkAllVps, 0, 1, TimeUnit.MINUTES);
        ipPoolSchedule.scheduleAtFixedRate(this::updateIpPool, 1, 1, TimeUnit.MINUTES);
        healthCheckSchedule.scheduleAtFixedRate(() -> printIpPool(log, "vps", ipPool), 0, 5, TimeUnit.SECONDS);
    }

    void updateIpPool() {
        // 获取所有vps实例
        getVpsInstancesFromDb();
        if (CollectionUtils.isEmpty(vpsInstances)) {
            log.info("vps实例为空, quit");
            return;
        }
        // 排除错误实例
        if (!errorInstance.isEmpty()) {
            log.info("排除错误vps: {}", JSON.toJSONString(errorInstance.stream().map(VpsInstance::getIpAddr).distinct().collect(Collectors.toList())));
            log.info("排除之前: {}", JSON.toJSONString(vpsInstances.stream().map(VpsInstance::getIpAddr).distinct().collect(Collectors.toList())));
            vpsInstances.removeAll(errorInstance);
            log.info("排除之后: {}", JSON.toJSONString(vpsInstances.stream().map(VpsInstance::getIpAddr).distinct().collect(Collectors.toList())));
            errorInstance.clear();
            if (CollectionUtils.isEmpty(vpsInstances)) {
                log.error("排除错误vps之后, 没有可用vps, quit");
                return;
            }
        }
        // 初始化所有VPS的SSH连接
        initSSH(vpsInstances);
        long begin = System.currentTimeMillis();
        // 初始化ip池: 获取所有vps的 ppp0 网卡的ip
        List<ProxyIp> allLatestProxyIp = vpsFetchService.getAllLatestProxyIp(vpsInstances);
        if (CollectionUtils.isEmpty(allLatestProxyIp)) {
            log.error("初始化ip池失败");
            return;
        }
        List<String> collect2 = allLatestProxyIp.stream().map(ProxyIp::getIpAddrWithTimeAndValid).collect(Collectors.toList());
        log.info("获取所有vps的 ppp0 网卡的ip, cost={} ms, size={}, 代理ip列表={}", (System.currentTimeMillis() - begin), allLatestProxyIp.size(), collect2.toString());
        // 加入ip池
        allLatestProxyIp.forEach(this::addIpPool);
        List<String> collect = ipPool.stream().map(ProxyIp::getIpWithVps).distinct().collect(Collectors.toList());
        log.info("ip池更新完成, size={}, data={}", collect.size(), collect.toString());
    }

    /**
     * 加入ip池
     *
     * @param vi vps实例
     */
    void addIpPool(VpsInstance vi) {
        if (vi == null) {
            return;
        }
        ProxyIp proxyIp = vpsFetchService.getSingleVpsInstanceIp(vi);
        addIpPool(proxyIp);
    }

    public void addIpPool(ProxyIp proxyIp) {
        if (proxyIp == null) {
            log.error("proxyIp is null, quit");
            return;
        }
        if (ipPool.isEmpty()) {
            log.info("ipPool为空,直接添加, proxyIp={}", proxyIp.getIpAddrWithTimeAndValid());
            ipPool.offer(proxyIp);
            return;
        }
        String vpsAddr = proxyIp.getVpsInstance().getIpAddr();
        List<String> vpsCollect = ipPool.stream().filter(e -> e.getValid().get()).map(e -> e.getVpsInstance().getIpAddr()).distinct().collect(Collectors.toList());
        if (vpsCollect.isEmpty()) {
            log.info("vps列表为空, 直接添加, proxyIp={}", proxyIp.getIpAddrWithTimeAndValid());
            ipPool.offer(proxyIp);
            return;
        }
        if (!vpsCollect.contains(vpsAddr)) {
            log.info("vps在ip池中不存在, 直接添加, proxyIp={}", proxyIp.getIpAddrWithTimeAndValid());
            ipPool.offer(proxyIp);
            return;
        }
        for (ProxyIp ip : ipPool) {
            String oldVpsAddr = ip.getVpsInstance().getIpAddr();
            if (oldVpsAddr.equalsIgnoreCase(vpsAddr)) {
                ip.getValid().set(false);
                ipPool.offer(proxyIp);
                log.info("加入ip池, proxyIp={}, vps={}", proxyIp.getIpAddrWithTimeAndValid(), proxyIp.getVpsInstance().getIpAddr());
                return;
            }
        }
    }

    void replayVps2() {
        int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
        while (true) {
            try {
                if (replayVpsQueue.isEmpty()) {
                    log.info("等待重播vps队列为空, quit");
                    Thread.sleep(5000L);
                    continue;
                }
                List<String> collect = replayVpsQueue.stream().map(VpsInstance::getIpAddr).collect(Collectors.toList());
                log.info("待重播ip数={}, data={}", replayVpsQueue.size(), JSON.toJSONString(collect));
                VpsInstance vpsInstance = replayVpsQueue.poll();
                if (Objects.isNull(vpsInstance)) {
                    log.error("vpsInstance is null, quit");
                    continue;
                }
                int retry = 1;
                boolean isAddQueue = true;
                while (retry <= maxRetryForCheckVpsAlive) {
                    boolean changeVpsStatus = restartVps(vpsInstance);
                    log.info("重播vps, 是否成功={}, vps={}, retry={}", changeVpsStatus, vpsInstance.getIpAddr(), retry);
                    if (changeVpsStatus) {
                        // 重播成功 放入ip池
                        addIpPool(vpsInstance);
                        isAddQueue = false;
                        break;
                    }
                    retry++;
                }
                if (isAddQueue) {
                    log.info("重播失败, 重新加入等待队列, vps={}", vpsInstance.getInstanceInfo());
                    addReplayVpsQueue(vpsInstance);
                }
            } catch (Exception e) {
                log.error("重播vps异常, cause={}", e.getMessage());
            }
        }
    }

    void addReplayVpsQueue(VpsInstance vpsInstance) {
        if (vpsInstance == null) {
            return;
        }
        String ipAddr = vpsInstance.getIpAddr();
        List<String> collect = replayVpsQueue.stream().map(VpsInstance::getIpAddr).distinct().collect(Collectors.toList());
        if (!collect.contains(ipAddr)) {
            log.info("加入待重播队列, vps={}", vpsInstance.getIpAddr());
            replayVpsQueue.offer(vpsInstance);
        } else {
            log.info("加入待重播队列失败, 已存在, quit");
        }
    }

    /**
     * 改变vps状态(重启)
     *
     * @param vi
     * @return
     */
    boolean restartVps(VpsInstance vi) {
        if (vi == null) {
            return false;
        }
        try {
            // SSH 远程连接
            Session session = JschUtil.getSession(vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
            if (session == null) {
                log.error("session is null, quit, vps={}", vi.getIpAddrUsernamePwd());
                return false;
            }
            String oldIp = getOldIp(session, vi);
            if (StringUtils.isBlank(oldIp)) {
                log.error("oldIp is null, quit, vps={}", vi.getIpAddr());
                return false;
            }
            int retry = 0;
            // 最多重试两次
            while (retry < 2) {
                retry++;
                execRestart(session);
                String newIp = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
                if (StringUtils.isBlank(newIp)) {
                    log.info("newIp获取失败, vps={}, newIp={}", vi.getIpAddr(), newIp);
                    continue;
                }
                String oldIpStr = oldIp.replaceAll("\n", "");
                String newIpStr = newIp.replaceAll("\n", "");
                //log.info("oldIp={}, oldIpStr={}, newIp={}, newIpStr={}", oldIp.replaceAll("\n", ""), oldIpStr, newIp.replaceAll("\n", ""), newIpStr);
                if (oldIpStr.equals(newIpStr)) {
                    log.info("replay失败, ip无变化, vps={}, oldIp={}, newIp={}", vi.getIpAddr(), oldIpStr, newIpStr);
                } else {
                    log.info("replay成功, vps={}, oldIp={}, newIp={}", vi.getIpAddr(), oldIpStr, newIpStr);
                    return true;
                }
            }
            log.info("重试2次均失败, vps={}", vi.getIpAddr());
            AtomicInteger oldVal = vpsAlarmCache.putIfAbsent(vi, new AtomicInteger(1));
            if (oldVal != null) {
                oldVal.incrementAndGet();
            }
            return false;
        } catch (Exception e) {
            log.error("replay异常, vps={}, cause={}", vi.getIpAddr(), e.getMessage(), e);
            return false;
        }
    }

    String getOldIp(Session session, VpsInstance vi) {
        try {
            String oldIp = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
            if (StringUtils.isNotBlank(oldIp)) {
                log.info("vps={}, oldIp: {}", vi.getIpAddr(), oldIp.replaceAll("\n", ""));
                return oldIp;
            }
            // 不能获取代理ip
            log.info("oldIp获取失败, vps={}", vi.getIpAddr());
            // 旧ip不存在了，有两种情况：1.机器宕机了或者没有重拨网卡 2.tinyproxy进程下线了
            String tinyproxyPid = JschUtil.exec(session, VpsConfig.Operate.tinyproxy_alive.getCommand(), CharsetUtil.CHARSET_UTF_8);
            if (StringUtils.isBlank(tinyproxyPid)) {
                // tinyproxy 下线了，重启
                String restartTinyproxy = JschUtil.exec(session, VpsConfig.Operate.restart_tinyproxy.getCommand(), CharsetUtil.CHARSET_UTF_8);
                log.info("tinyproxy下线, 重启tinyproxy, vps={}, exec={}", vi.getIpAddr(), restartTinyproxy);
                // 不能在这递归获取ip, 防止一直不成功导致死循环
                return null;
            } else {
                log.error("tinyproxy存活, Pid={}, 但获取oldIp失败, 尝试强制重启, vps={}", tinyproxyPid, vi.getIpAddr());
                // 重播网卡: 重启命令都是返回空，所以不能判断字符串
                execRestart(session);
                // 检查存活
                String ip2 = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
                if (StringUtils.isNotBlank(ip2)) {
                    log.info("强制重启成功, vps={}, ip={}", vi.getIpAddr(), ip2);
                    return ip2;
                } else {
                    log.error("强制重启失败, vps={}", vi.getIpAddr());
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("获取oldIp异常: {}", e.getMessage());
        }
        return null;
    }

    static void execRestart(Session session) {
        try {
            JschUtil.exec(session, VpsConfig.Operate.stop.getCommand(), CharsetUtil.CHARSET_UTF_8);
            JschUtil.exec(session, VpsConfig.Operate.start.getCommand(), CharsetUtil.CHARSET_UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 每2分钟检查一次所有的vps机器，失联的发送告警邮件
     */
    void checkAllVps() {
        if (CollectionUtils.isEmpty(vpsInstances)) {
            log.warn("vpsInstances is empty, quit");
            return;
        }
        List<String> detail = vpsInstances.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("VPS机器数={}, detail={}", vpsInstances.size(), JSON.toJSONString(detail));
        CountDownLatch countDownLatch = new CountDownLatch(vpsInstances.size());
        int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
        CopyOnWriteArrayList<VpsInstance> errorVps = new CopyOnWriteArrayList<>();
        try {
            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
            assert defaultTunnel != null;
            String checkUrl = defaultTunnel.getType() == TunnelType.VPS_OVERSEA.getId() ? HttpCons.OVERSEA_IP_ALIVE_CHECK_URL : HttpCons.DOMESTIC_IP_ALIVE_CHECK_URL;
            vpsInstances.forEach(vi -> ipAliveCheckThreadPool.submit(() -> {
                try {
                    if (!isVpsInstanceAlive(vi, maxRetryForCheckVpsAlive, checkUrl)) {
                        errorVps.add(vi);
                        // 加入重播队列
                        addReplayVpsQueue(vi);
                    }
                } catch (Exception e) {
                    log.error("检查vps存活异常, cause={}", e.getMessage(), e);
                } finally {
                    countDownLatch.countDown();
                }
            }));
            countDownLatch.await();
            if (!errorVps.isEmpty()) {
                List<String> distinct = errorVps.stream().filter(Objects::nonNull).map(VpsInstance::getIpAddrUsernamePwd).distinct().collect(Collectors.toList());
                log.error("失联机器: {}", JSON.toJSONString(distinct));
                String subject = "VPS心跳检测告警, 失联机器数量: " + distinct.size();
                StringJoiner joiner = new StringJoiner(System.lineSeparator());
                joiner.add("失联机器: ");
                distinct.forEach(joiner::add);
                String content = joiner.toString();
//                int sendAlarmEmail = tunnelInitService.getDefaultTunnel().getSendAlarmEmail();
//                if (sendAlarmEmail == 1) {
//                    sendMailService.sendMail(subject, content);
//                }
                String msg = subject + System.lineSeparator() + content;
                enterpriseWeChatRobotSerice.send(msg);
                errorInstance.addAll(errorVps);
            }
        } catch (Exception e) {
            log.info("检查所有vps机器存活异常, cause={}", e.getMessage(), e);
        }
    }

    /**
     * 检查单个vps实例是否存活
     *
     * @param vi       vps实例
     * @param maxRetry 最大重试次数
     * @return
     */
    boolean isVpsInstanceAlive(VpsInstance vi, int maxRetry, String checkUrl) {
        if (vi == null) {
            return false;
        }
        int retry = 0;
        Session session = JschUtil.getSession(vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
        while (retry <= maxRetry) {
            retry++;
            Response response = null;
            try {
//                // tinyproxy 存活 后续改为nginx可用
//                String tinyproxyAlive = JschUtil.exec(session, VpsConfig.Operate.tinyproxy_alive.getCommand(), CharsetUtil.CHARSET_UTF_8);
//                if (StringUtils.isBlank(tinyproxyAlive)) {
//                    log.error("检活vps失败, tinyproxy不存在, vps={}, retry={}", vi.getInstanceInfo(), retry);
//                    continue;
//                }
                // ppp0 网卡的代理ip存在
                String proxyIp = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8).replaceAll("\n", "");
                if (StringUtils.isBlank(proxyIp)) {
                    log.error("检活vps失败, 代理ip不存在, vps={}, retry={}", vi.getInstanceInfo(), retry);
                    continue;
                }
                response = OkHttpTool.doGetByProxyIp(checkUrl, proxyIp, vpsConfig.getDefaultPort(), vpsConfig.getUsername(), vpsConfig.getPassword(),
                        30, 30, 30);
                if (response.code() != 200) {
                    log.error("检活vps失败, response code not 200, vps={}, retry={}", vi.getInstanceInfo(), retry);
                    continue;
                }
                return true;
            } catch (Exception e) {
                log.error("检活vps异常, vps={}, retry={}, cause={}", vi.getInstanceInfo(), retry, e.getMessage());
            } finally {
                OkHttpTool.closeResponse(response);
            }
        }
        return false;
    }


//    /**
//     * 刷新ip池
//     */
//    @Scheduled(cron = "0 0/5 * * * ?")
//    void refreshIpPool() {
//        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
//            return;
//        }
//        List<ProxyIp> ipList = vpsFetchService.getAllLatestProxyIp(vpsInstances);
//        if (ipList.isEmpty()) {
//            log.error("ipList is empty, quit");
//            return;
//        }
//        // 删除原来的
//        ipPool.forEach(e->e.getValid().set(false));
//        log.info("标记删除原来的ip池: {}", JSON.toJSONString(ipPool));
//        // 添加最新的
//        ipList.forEach(ipPool::offer);
//        log.info("更新ip池后: {}", JSON.toJSONString(ipPool));
//    }

    /**
     * 初始化所有VPS的SSH连接
     */
    void initSSH(List<VpsInstance> vpsInstances) {
        if (CollectionUtils.isEmpty(vpsInstances)) {
            throw new RuntimeException("初始化SSH连接失败");
        }
        try {
            for (VpsInstance vi : vpsInstances) {
                // 连接后放入了缓存
                Session session = JschUtil.getSession(vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
                session.setTimeout(5000);
                log.info("init SSH: {}", vi.getInstanceInfo());
            }
        } catch (Exception e) {
            log.error("初始化SSH连接失败: {}", e.getMessage(), e);
        }
    }

//    @Scheduled(cron = "0/5 * * * * ?")
//    void printIpPool() {
//        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
//            return;
//        }
//        printIpPool(log, "vps", ipPool);
//    }
//
//    /**
//     * 从数据库同步vps实例列表
//     */
//    @Scheduled(cron = "0/10 * * * * ?")

    void getVpsInstancesFromDb() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC_STATIC)) {
            return;
        }
        List<VpsInstance> collect = vpsInstanceService.list().stream()
                .filter(e -> Objects.nonNull(e) && e.getValid() == 1 && e.getType() == TunnelType.VPS_DOMESTIC_STATIC.getId())
                .distinct()
                .collect(Collectors.toList());
        List<String> info = collect.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("从数据库同步vps实例列表: {}", JSON.toJSONString(info));
        vpsInstances = Optional.ofNullable(collect).orElse(new LinkedList<>());
    }

    @Override
    public void destroy() throws Exception {
        JschUtil.closeAll();
        System.out.println("关闭所有SSH会话");
    }

    @Override
    public ProxyIp getOne() {
        TunnelInstance tunnelInstance = tunnelInitService.getDefaultTunnel();
        if (tunnelInstance == null) {
            log.error("get ip error, tunnelInstance is null");
            return null;
        }
        ProxyIp poll = ipPool.poll();
        if (Objects.isNull(poll)) {
            log.error("the ip from queue is null");
            return null;
        }
        // 只取有效的
        boolean valid = poll.getValid().get();
        if (!valid) {
            log.info("ip={} is invalid and will be removed", poll.getIpAddr());
            return getOne();
        }
        // 取了需要再放进去
        ipPool.offer(poll);
        return poll;
    }

    @Override
    public void addWhiteList() {

    }

    @Override
    public void healthCheck() {

    }

    @Override
    public void successPercentStatistics() {

    }

    @Override
    public void addFixedNumIp(int num) {

    }

    @Override
    public void removeIp(String ip, int port) {

    }

    @Override
    public ConcurrentLinkedQueue<ProxyIp> getIpPool() {
        return ipPool;
    }
}
