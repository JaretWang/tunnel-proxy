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
import com.dataeye.proxy.cons.Log;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.service.SendMailService;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/11/7 14:24
 * @description vps ip 选择器
 */
@Component
public class VpsIpSelector implements CommonIpSelector, DisposableBean {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("VpsIpSelector");
    private final ConcurrentLinkedQueue<ProxyIp> ipPool = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ProxyIp> waitingReplayVpsQueue = new ConcurrentLinkedQueue<>();
    //    private final ConcurrentLinkedQueue<ProxyIpTimer> waitingReplayVpsQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService checkAllVpsSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("checkAllVpsSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService releaseProxyIpWhenWaitTimeThresholdSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("releaseProxyIpWhenWaitTimeThresholdSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService replayVpsSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("replayVpsSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService fixedTimeReplaySchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("fixedTimeReplaySchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService markIpStatusSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("markIpStatusSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService getAllVpsIpSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("getAllVpsIpSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService getVpsInstancesFromDbSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("getVpsInstancesFromDbSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    @Getter
    private final ConcurrentHashMap<VpsInstance, AtomicInteger> vpsAlarmCache = new ConcurrentHashMap<>();
    @Autowired
    VpsConfig vpsConfig;
    @Autowired
    VpsInstanceService vpsInstanceService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    SendMailService sendMailService;
    @Resource(name = "replayVps")
    ThreadPoolTaskExecutor replayVpsThreadPool;
    @Resource(name = "markIpStatus")
    ThreadPoolTaskExecutor markIpStatusThreadPool;
    @Resource(name = "ipAliveCheck")
    ThreadPoolTaskExecutor ipAliveCheckThreadPool;
    @Resource
    TunnelInitService tunnelInitService;
    @Getter
    List<VpsInstance> vpsInstances;
    @Autowired
    VpsFetchServiceImpl vpsFetchService;

    @Override
    public void init() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC)) {
            return;
        }
        // 获取所有vps实例
        getVpsInstancesFromDb(TunnelType.VPS_DOMESTIC);
        // 初始化所有VPS的SSH连接
        initSSH(vpsInstances);
        // 初始化ip池: 获取所有vps的 ppp0 网卡的ip
        List<ProxyIp> ipList = vpsFetchService.getAllLatestProxyIp(vpsInstances);
        if (CollectionUtils.isEmpty(ipList)) {
            throw new RuntimeException("初始化ip池失败");
        }
        // 将代理ip加入到ip池
        ipList.forEach(ipPool::offer);
        List<String> collect = ipPool.stream().map(ProxyIp::getIpWithVps).collect(Collectors.toList());
        log.info("初始化ip池完成, size={}, data={}", collect.size(), collect.toString());
        // 检查所有vps存活，并发送告警邮件
        checkAllVpsSchedule.scheduleAtFixedRate(this::checkAllVps, 0, 2, TimeUnit.MINUTES);
        // 解决长时间未释放连接的ip(超过60s)，直接将状态标记为false, 连接数设置为0，并放入待重播队列
        releaseProxyIpWhenWaitTimeThresholdSchedule.scheduleAtFixedRate(this::releaseProxyIpWhenWaitTimeThreshold, 0, 5, TimeUnit.SECONDS);
        // 定时检测出1个成功率最低的vps，放入待重播队列
        fixedTimeReplaySchedule.scheduleAtFixedRate(this::fixedTimeReplay, 0, 5, TimeUnit.MINUTES);
        // 根据ip成功率和是否可用标记ip状态，并放入待重播队列
        markIpStatusSchedule.scheduleAtFixedRate(this::markIpStatus, 0, 1, TimeUnit.MINUTES);
        // 将待重播队列里面的vps进行重播
        replayVpsSchedule.scheduleAtFixedRate(this::replayVps, 0, 5, TimeUnit.SECONDS);
        // 获取所有vps的 ppp0 网卡的ip
        getAllVpsIpSchedule.scheduleAtFixedRate(vpsFetchService::scheduleGetAllVpsIp, 0, 1, TimeUnit.MINUTES);
        // 从数据库同步vps实例列表
        getVpsInstancesFromDbSchedule.scheduleAtFixedRate(()-> this.getVpsInstancesFromDb(TunnelType.VPS_DOMESTIC), 0, 10, TimeUnit.SECONDS);
    }

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

    @Scheduled(cron = "0/5 * * * * ?")
    void printIpPool() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC)) {
            return;
        }
        printIpPool(log, "vps", ipPool);
    }

    /**
     * 从数据库同步vps实例列表
     */
//    @Scheduled(cron = "0/10 * * * * ?")
    void getVpsInstancesFromDb(TunnelType tunnelType) {
        List<VpsInstance> collect = vpsInstanceService.list().stream()
                .filter(e -> Objects.nonNull(e) && e.getValid() == 1 && tunnelType.getId() == e.getType())
                .distinct()
                .collect(Collectors.toList());
        List<String> info = collect.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("从数据库同步vps实例列表: {}", JSON.toJSONString(info));
        vpsInstances = Optional.ofNullable(collect).orElse(new LinkedList<>());
    }

    /**
     * 标记ip状态
     * 1.成功率低于80%，标记为无效(注: 标记之前检查ip池总数是否低于阈值， 阈值=vps机器数 * 1/2,如果达到最小阈值，则不能标记为无效，只能降低优先级，避免ip池全部标记为无效ip，没有可用ip，或者可用ip越来越少，导致所有的请求压力都来到了剩下的ip，然后剩下的ip又抗不住，然后接着雪崩)
     * 2.守护线程进行ip检活（注：三次尝试连接ip都连接不上），将失联的ip标记为无效
     */
//    @Scheduled(cron = "0 0/1 * * * ?")
    void markIpStatus() {
        try {
            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
            int minSuccessPercentForRemoveIp = defaultTunnel.getMinSuccessPercentForRemoveIp();
            int minUseTimes = defaultTunnel.getMinUseTimesForRemoveIp();
            int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
            CountDownLatch countDownLatch = new CountDownLatch(ipPool.size());
            String checkUrl = defaultTunnel.getType() == TunnelType.VPS_DOMESTIC.getId() ? HttpCons.OVERSEA_IP_ALIVE_CHECK_URL : HttpCons.DOMESTIC_IP_ALIVE_CHECK_URL;
            for (ProxyIp proxyIp : ipPool) {
                markIpStatusThreadPool.submit(() -> {
                    try {
                        boolean check1 = isSuccessPercentNormal(proxyIp, minSuccessPercentForRemoveIp, minUseTimes);
                        VpsInstance vpsInstance = proxyIp.getVpsInstance();
                        boolean check2 = isProxyIpAlive(proxyIp, maxRetryForCheckVpsAlive, checkUrl);
                        if (!check1 || !check2) {
                            log.info("标记ip无效状态, 成功率高于{}%={}, 代理ip存活={}, proxyIp={}, vps={}",
                                    minSuccessPercentForRemoveIp, check1, check2, proxyIp.getIpAddrWithTimeAndValid(), proxyIp.getVpsInstance().getInstanceInfo());
                            proxyIp.getValid().set(false);
                            if (vpsInstance != null) {
                                addwaitingReplayVpsQueue(proxyIp);
                            }
                        }
                    } catch (Exception e) {
                        log.error("标记ip状态异常, cause={}", e.getMessage(), e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }
            countDownLatch.await();
        } catch (Exception e) {
            log.error("标记ip状态异常, cause={}", e.getMessage());
        }
    }

    /**
     * 检查ip成功率是否正常(是否大于阈值)
     *
     * @param proxyIp 代理ip
     * @return
     */
    boolean isSuccessPercentNormal(ProxyIp proxyIp, double minSuccessPercent, int minUseTimes) {
        if (proxyIp == null) {
            log.info("proxyIp is null, quit");
            return false;
        }
        AtomicBoolean valid = proxyIp.getValid();
        if (!valid.get()) {
            log.info("proxyIp is not valid, quit");
            return false;
        }
        // 避免ip一次都没用，所以成功率是0，但是判定为重播了
        AtomicLong useTimes = proxyIp.getUseTimes();
        AtomicLong connecting = proxyIp.getConnecting();
        AtomicLong okTimes = proxyIp.getOkTimes();
        // 防止ip还没有用，就又被重播了
        if (useTimes.get() < minUseTimes || connecting.get() == 0 || okTimes.get() == 0) {
            log.info("proxyIp times=0, quit, proxyIp={}", JSON.toJSONString(proxyIp));
            return true;
        }
        double successPercent = getSuccessPercent(proxyIp);
        log.info("successPercent={}, min={}, proxyIp={}", successPercent, minSuccessPercent, proxyIp.getIpAddrWithTimeAndValid());
        return successPercent >= minSuccessPercent;
    }

    /**
     * 获取成功率
     *
     * @param proxyIp 代理ip
     * @return 成功率 double 值，保留两位小数
     */
    double getSuccessPercent(ProxyIp proxyIp) {
        double rate = 0d;
        if (proxyIp == null) {
            return rate;
        }
        AtomicLong okTimes = proxyIp.getOkTimes();
        AtomicLong errorTimes = proxyIp.getErrorTimes();
        if (okTimes == null || errorTimes == null) {
            return rate;
        }
        if (okTimes.get() <= 0) {
            return 0d;
        }
        long total = okTimes.get() + errorTimes.get();
        if (total <= 0) {
            Log.SERVER.error("total error, ProxyIp={}, total={}", proxyIp.getIpAddrWithTimeAndValid(), total);
            return rate;
        }
        return new BigDecimal(okTimes.get()).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP).doubleValue();
    }

    boolean isProxyIpAlive(ProxyIp proxyIp, int maxRetry, String checkUrl) {
        if (proxyIp == null) {
            return false;
        }
        int retry = 0;
        while (retry <= maxRetry) {
            retry++;
            Response response = null;
            try {
                response = OkHttpTool.doGetByProxyIp(checkUrl, proxyIp.getHost(), proxyIp.getPort(), proxyIp.getUserName(), proxyIp.getPassword(), 5, 5, 5);
                if (response.code() == 200) {
                    return true;
                } else {
                    log.info("检活失败, proxyIp={}, retry={}, checkUrl={}", proxyIp.getIpAddrWithTimeAndValid(), retry, checkUrl);
                }
            } catch (Exception e) {
                log.error("检活异常, proxyIp={}, retry={}, checkUrl={}, cause={}", proxyIp.getIpAddrWithTimeAndValid(), retry, checkUrl, e.getMessage());
            } finally {
                OkHttpTool.closeResponse(response);
            }
        }
        return false;
    }

    /**
     * 检查单个vps实例是否存活
     *
     * @param vi       vps实例
     * @param maxRetry 最大重试次数
     * @return
     */
    boolean isVpsInstanceAlive(VpsInstance vi, int maxRetry) {
        if (vi == null) {
            return false;
        }
        int retry = 0;
        while (retry <= maxRetry) {
            retry++;
            try {
                // 检查tinyproxy
                //String exec = CommandUtils.exec(VpsConfig.Operate.tinyproxy_alive.getCommand(), vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
                Session session = JschUtil.getSession(vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
                String exec = JschUtil.exec(session, VpsConfig.Operate.tinyproxy_alive.getCommand(), CharsetUtil.CHARSET_UTF_8);
                if (StringUtils.isNotBlank(exec)) {
                    return true;
                } else {
                    log.error("检活tinyproxy失败, vps={}, retry={}", vi.getInstanceInfo(), retry);
                }
            } catch (Exception e) {
                log.error("检活tinyproxy异常, vps={}, retry={}, cause={}", vi.getInstanceInfo(), retry, e.getMessage());
            }
        }
        return false;
    }

    //    @Scheduled(cron = "0 0/5 * * * ?")
    void fixedTimeReplay() {
        if (ipPool.isEmpty()) {
            log.error("定时重拨失败, ipPool is empty, quit");
            return;
        }

        // 随机重播一个队列尾部ip (优化：按照成功率排序，重播成功率低的ip)，但vps有可能还在被使用，所以暂时先标记失效（意味着不再接收连接）
        Map<ProxyIp, Double> collect = ipPool.stream()
                .filter(Objects::nonNull)
                .sorted((o1, o2) -> {
                    double successPercent1 = getSuccessPercent(o1);
                    double successPercent2 = getSuccessPercent(o2);
                    return (int) (successPercent1 - successPercent2);
                })
                .collect(Collectors.toMap(e -> e, this::getSuccessPercent, (e1, e2) -> e1));
        if (collect.isEmpty()) {
            log.error("经过排序后的代理IP为空");
            return;
        }

        // print
        StringJoiner joiner = new StringJoiner(",");
        collect.forEach((k, v) -> joiner.add(k.getIpAddrWithTimeAndValid() + " -> " + v));
        log.info("按照成功率排序后, collect={}", joiner.toString());

        List<ProxyIp> proxyIpList = collect.keySet().stream().distinct().collect(Collectors.toList());
        if (proxyIpList.isEmpty()) {
            return;
        }
        // 取最后一个，放入待重播队列
        ProxyIp proxyIp = proxyIpList.get(proxyIpList.size() - 1);
        log.info("取成功率最低的ip={}", proxyIp.getIpAddrWithTimeAndValid());
        if (ipPool.size() <= 1) {
            log.info("ip池只有一个ip, 不重播, quit");
            return;
        }
        proxyIp.getValid().set(false);
        addwaitingReplayVpsQueue(proxyIp);
    }

    /**
     * 解决长时间未释放连接的ip(超过60s)，直接将状态标记为false, 连接数设置为0
     */
//    @Scheduled(cron = "0/5 * * * * ?")
    void releaseProxyIpWhenWaitTimeThreshold() {
        if (waitingReplayVpsQueue.isEmpty()) {
            log.info("waitingReplayVpsQueue is empty, quit");
            return;
        }
        log.info("等待队列ip：{}", getIpPoolSimpleInfo(waitingReplayVpsQueue));
    }

    /**
     * 重播VPS
     * 安全重播的条件：
     * 1.ip被标记无效时, 并且等待ip未处理完的连接数处理完成（但不能超过最大时间30s），只有正在处理的连接数变为0，才能从ip池中移除，并且重播vps
     * 2.需要主动重播更换ip时
     */
//    @Scheduled(cron = "0/5 * * * * ?")
    void replayVps() {
        if (waitingReplayVpsQueue.isEmpty()) {
            log.info("等待重播vps队列为空, quit");
            return;
        }
        try {
            log.info("待重播ip数={}, data={}", waitingReplayVpsQueue.size(), getIpPoolSimpleInfo(waitingReplayVpsQueue));
            // 筛选出连接数为0的代理ip

            List<ProxyIp> collect = getRealReplayIp();
            List<String> real = collect.stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
            log.info("实际待重播ip数={}, data={}", collect.size(), JSON.toJSONString(real));
            if (collect.isEmpty()) {
                log.info("待重播vps数为0, quit");
                return;
            }
            CountDownLatch countDownLatch = new CountDownLatch(collect.size());
            int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
            for (ProxyIp proxyIp : collect) {
                replayVpsThreadPool.submit(() -> {
                    try {
                        VpsInstance vpsInstance = proxyIp.getVpsInstance();
                        int retry = 1;
                        while (retry <= maxRetryForCheckVpsAlive) {
                            boolean changeVpsStatus = restartVps(vpsInstance);
                            log.info("重播vps, 是否成功={}, vps={}", changeVpsStatus, vpsInstance.getIpAddr());
                            if (changeVpsStatus) {
                                // 重播成功 放入ip池
                                addIpPool(vpsInstance);
                                return;
                            }
                            retry++;
                        }
                        addwaitingReplayVpsQueue(proxyIp);
                    } catch (Exception e) {
                        log.error("重播vps异常, cause={}", e.getMessage(), e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }
            countDownLatch.await();
            List<String> collect2 = getRealReplayIp().stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
            log.info("重播完后, 待重播队列ip数={}, data={}", collect2.size(), JSON.toJSONString(collect2));
        } catch (Exception e) {
            log.error("重播vps异常, cause={}", e.getMessage());
        }
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
        String ipAddr = proxyIp.getIpAddr();
        List<String> collect = ipPool.stream().map(ProxyIp::getIpAddr).distinct().collect(Collectors.toList());
        if (!collect.contains(ipAddr)) {
            log.info("加入ip池, proxyIp={}, vps={}", proxyIp.getIpAddrWithTimeAndValid(), proxyIp.getVpsInstance().getIpAddr());
            ipPool.offer(proxyIp);
        } else {
            log.info("加入ip池失败, 已存在, proxyIp={}, vps={}", proxyIp.getIpAddrWithTimeAndValid(), proxyIp.getVpsInstance().getIpAddr());
        }
    }

    /**
     * 加入之前需要检查是否已经存在
     *
     * @param proxyIp
     */
    void addwaitingReplayVpsQueue(ProxyIp proxyIp) {
        if (proxyIp == null) {
            return;
        }
        String ipAddr = proxyIp.getIpAddr();
        List<String> collect = waitingReplayVpsQueue.stream().map(ProxyIp::getIpAddr).distinct().collect(Collectors.toList());
        if (!collect.contains(ipAddr)) {
            log.info("加入待重播队列, proxyIp={}, vps={}", proxyIp.getIpAddrWithTimeAndValid(), proxyIp.getVpsInstance().getIpAddr());
            waitingReplayVpsQueue.offer(proxyIp);
        }
    }

    /**
     * 从等待重播队列筛选满足条件的代理ip
     *
     * @return 满足条件的ip
     */
    List<ProxyIp> getRealReplayIp() {
        List<ProxyIp> result = new LinkedList<>();
        log.info("待重放队列的ip数量={}", waitingReplayVpsQueue.size());
        while (!waitingReplayVpsQueue.isEmpty()) {
            ProxyIp poll = waitingReplayVpsQueue.poll();
            if (Objects.nonNull(poll) && !poll.getValid().get() && poll.getConnecting().get() == 0) {
                result.add(poll);
            }
        }
        List<ProxyIp> collect = result.stream().distinct().collect(Collectors.toList());
        log.info("满足条件的ip数量={}", collect.size());
        return collect;
    }

    String getIpPoolSimpleInfo(ConcurrentLinkedQueue<ProxyIp> queue) {
        List<String> collect = queue.stream().map(ProxyIp::getIpAddrWithTimeAndValid).collect(Collectors.toList());
        return JSON.toJSONString(collect);
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

    public static void main(String[] args) {
//        Session session = JschUtil.getSession("154.37.50.4", 20013, "root", "6ec55e0213f7");
////        Session session = JschUtil.getSession("154.37.50.4", 20035, "root", "6ec55e0213f7");
//        String exec1 = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
//        System.out.println(exec1);
//        execRestart(session);
//        String exec2 = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
//        System.out.println(exec2);
//        JschUtil.close(session);
    }

    static void execRestart(Session session) {
        try {
            JschUtil.exec(session, VpsConfig.Operate.stop.getCommand(), CharsetUtil.CHARSET_UTF_8);
            JschUtil.exec(session, VpsConfig.Operate.start.getCommand(), CharsetUtil.CHARSET_UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
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
                    log.info("强制重启成功, vps={}, ip={}", vi.getIpAddr(), ip2.replaceAll("\n", ""));
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

    /**
     * 每2分钟检查一次所有的vps机器
     */
//    @Scheduled(cron = "0 0/2 * * * ?")
    void checkAllVps() {
        if (CollectionUtils.isEmpty(vpsInstances)) {
            log.warn("vpsInstances is empty, quit");
            return;
        }
        List<String> detail = vpsInstances.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("VPS机器数={}, detail={}", vpsInstances.size(), JSON.toJSONString(detail));
        CopyOnWriteArrayList<VpsInstance> errorInstance = new CopyOnWriteArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(vpsInstances.size());
        int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
        try {
            vpsInstances.forEach(vi -> ipAliveCheckThreadPool.submit(() -> {
                try {
                    if (!isVpsInstanceAlive(vi, maxRetryForCheckVpsAlive)) {
                        errorInstance.add(vi);
                    }
                } catch (Exception e) {
                    log.error("检查vps存活异常, cause={}", e.getMessage(), e);
                } finally {
                    countDownLatch.countDown();
                }
            }));
            countDownLatch.await();
            if (!errorInstance.isEmpty()) {
                List<String> distinct = errorInstance.stream().filter(Objects::nonNull).map(VpsInstance::getInstanceInfo).distinct().collect(Collectors.toList());
                log.error("失联机器: {}", JSON.toJSONString(distinct));
                String subject = "VPS心跳检测告警, 失联机器数量: " + distinct.size();
                StringJoiner joiner = new StringJoiner(System.lineSeparator());
                joiner.add("失联机器: ");
                distinct.forEach(joiner::add);
                String content = joiner.toString();
                int sendAlarmEmail = tunnelInitService.getDefaultTunnel().getSendAlarmEmail();
                if (sendAlarmEmail == 1) {
                    sendMailService.sendMail(subject, content);
                }
            }
        } catch (Exception e) {
            log.info("检查所有vps机器存活异常, cause={}", e.getMessage(), e);
        }
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
