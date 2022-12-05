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
import com.dataeye.proxy.service.EnterpriseWeChatRobotSerice;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.IOException;
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
    private final ConcurrentLinkedQueue<VpsInstance> replayVpsQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService checkAllVpsSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("checkAllVpsSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    //    private final ConcurrentLinkedQueue<ProxyIpTimer> waitingReplayVpsQueue = new ConcurrentLinkedQueue<>();
    //    private final ScheduledExecutorService releaseProxyIpWhenWaitTimeThresholdSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("releaseProxyIpWhenWaitTimeThresholdSchedule-"), new ThreadPoolExecutor.AbortPolicy());
//    private final ScheduledExecutorService replayVpsSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("replayVpsSchedule-"), new ThreadPoolExecutor.AbortPolicy());
//    private final ScheduledExecutorService fixedTimeReplaySchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("fixedTimeReplaySchedule-"), new ThreadPoolExecutor.AbortPolicy());
//    private final ScheduledExecutorService markIpStatusSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("markIpStatusSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService healthCheck = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("markIpStatusSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService healthCheckSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("healthCheckSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService ipPoolSchedule = new ScheduledThreadPoolExecutor(1, new ThreadPoolConfig.TunnelThreadFactory("ipPoolSchedule-"), new ThreadPoolExecutor.AbortPolicy());
    private final CopyOnWriteArrayList<VpsInstance> errorInstance = new CopyOnWriteArrayList<>();
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
    EnterpriseWeChatRobotSerice enterpriseWeChatRobotSerice;
    @Autowired
    VpsFetchServiceImpl vpsFetchService;

    public static void main(String[] args) throws IOException {
//        Session session = JschUtil.getSession("154.37.50.4", 20013, "root", "6ec55e0213f7");
////        Session session = JschUtil.getSession("154.37.50.4", 20035, "root", "6ec55e0213f7");
//        String exec1 = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
//        System.out.println(exec1);
//        execRestart(session);
//        String exec2 = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
//        System.out.println(exec2);
//        JschUtil.close(session);

//        Response response = OkHttpTool.sendGetByProxy2(HttpCons.OVERSEA_IP_ALIVE_CHECK_URL, "154.31.38.33", 8000, "dataeye", "dataeye123", null, null);
//        System.out.println(response.body().string());
//        OkHttpTool.closeResponse(response);
        Response response2 = OkHttpTool.doGetByProxyIp(HttpCons.OVERSEA_IP_ALIVE_CHECK_URL, "154.31.38.33", 8000, "dataeye", "dataeye123",
                5, 5, 5);
        System.out.println(response2.body().string());
        OkHttpTool.closeResponse(response2);
    }

    static void execRestart(Session session) {
        try {
            JschUtil.exec(session, VpsConfig.Operate.stop.getCommand(), CharsetUtil.CHARSET_UTF_8);
            JschUtil.exec(session, VpsConfig.Operate.start.getCommand(), CharsetUtil.CHARSET_UTF_8);
        } catch (Exception e) {
            log.info("execRestart error, cause={}", e.getMessage(), e);
        }
    }

    @Override
    public void init() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS_DOMESTIC)) {
            return;
        }
        // 初始化ip池
        updateIpPool(true);
        if (ipPool.isEmpty()) {
            throw new RuntimeException("初始化ip池失败");
        }
        // 心跳检查（为了消化ip池长时间不用导致堆积的失效ip）
        healthCheck.scheduleAtFixedRate(this::heartBeatCheck, 0, 5, TimeUnit.MINUTES);
        // 检查ip成功率低于阈值, 代理ip连接不上，则标记ip失效，并放入待重播队列
//        markIpStatusSchedule.scheduleAtFixedRate(this::markIpStatus, 0, 1, TimeUnit.MINUTES);
        // 定时检测出1个成功率最低的vps，放入待重播队列
//        fixedTimeReplaySchedule.scheduleAtFixedRate(this::fixedTimeReplay, 0, 5, TimeUnit.MINUTES);
        // 解决长时间未释放连接的ip(超过60s)，直接将状态标记为false, 连接数设置为0，并放入待重播队列
//        releaseProxyIpWhenWaitTimeThresholdSchedule.scheduleAtFixedRate(this::releaseProxyIpWhenWaitTimeThreshold, 0, 5, TimeUnit.SECONDS);
        // 将待重播队列里面的vps进行重播
        //replayVpsSchedule.scheduleAtFixedRate(this::replayVps, 0, 5, TimeUnit.SECONDS);
        replayVpsThreadPool.submit(this::replayVps2);
        // 检查所有vps存活，并发送告警邮件
        checkAllVpsSchedule.scheduleAtFixedRate(this::checkAllVps, 0, 1, TimeUnit.MINUTES);
        // 定时更新ip池
        ipPoolSchedule.scheduleAtFixedRate(() -> updateIpPool(false), 1, 1, TimeUnit.MINUTES);
        healthCheckSchedule.scheduleAtFixedRate(() -> printIpPool(log, "vps", ipPool), 0, 5, TimeUnit.SECONDS);
    }

    /**
     * 更新ip池
     */
    void updateIpPool(boolean init) {
        // 获取所有vps实例
        List<VpsInstance> vpsInstancesFromDb = Optional.ofNullable(getVpsInstancesFromDb()).orElse(new LinkedList<>());
        if (CollectionUtils.isEmpty(vpsInstancesFromDb)) {
            log.info("vps实例为空, quit");
            return;
        }
        // 排除错误实例
        if (!errorInstance.isEmpty()) {
            log.info("排除错误vps: {}", JSON.toJSONString(errorInstance.stream().map(VpsInstance::getIpAddr).distinct().collect(Collectors.toList())));
            log.info("排除之前: {}", JSON.toJSONString(vpsInstancesFromDb.stream().map(VpsInstance::getIpAddr).distinct().collect(Collectors.toList())));
            vpsInstancesFromDb.removeAll(errorInstance);
            log.info("排除之后: {}", JSON.toJSONString(vpsInstancesFromDb.stream().map(VpsInstance::getIpAddr).distinct().collect(Collectors.toList())));
            errorInstance.clear();
            if (CollectionUtils.isEmpty(vpsInstancesFromDb)) {
                log.error("排除错误vps之后, 没有可用vps, quit");
                return;
            }
        }
        // 初始化所有VPS的SSH连接
        if (init) {
            initSSH(vpsInstancesFromDb);
        }
//        long begin = System.currentTimeMillis();
        // 初始化ip池: 获取所有vps的 ppp0 网卡的ip
        List<ProxyIp> allLatestProxyIp = vpsFetchService.getAllLatestProxyIp(vpsInstancesFromDb);
        if (CollectionUtils.isEmpty(allLatestProxyIp)) {
            log.error("初始化ip池失败");
            return;
        }
//        List<String> collect2 = allLatestProxyIp.stream().map(ProxyIp::getIpAddrWithTimeAndValid).collect(Collectors.toList());
//        log.info("获取所有vps的 ppp0 网卡的ip, cost={} ms, size={}, 代理ip列表={}", (System.currentTimeMillis() - begin), allLatestProxyIp.size(), collect2.toString());
        // 加入ip池
        allLatestProxyIp.forEach(this::addIpPool);
        List<String> collect = ipPool.stream().map(ProxyIp::getIpWithVps).distinct().collect(Collectors.toList());
        log.info("ip池更新完成, size={}, data={}", collect.size(), collect.toString());
        printIpPool(log, "vps", ipPool);
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

    /**
     * 从数据库同步vps实例列表
     */
    List<VpsInstance> getVpsInstancesFromDb() {
        List<VpsInstance> collect = vpsInstanceService.list().stream()
                .filter(e -> Objects.nonNull(e) && e.getValid() == 1 && TunnelType.VPS_DOMESTIC.getId() == e.getType())
                .distinct()
                .collect(Collectors.toList());
        List<String> info = collect.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("从数据库同步vps实例列表: size={}, data={}", info.size(), JSON.toJSONString(info));
        return collect;
    }

    /**
     * 标记ip状态
     * 1.成功率低于80%，标记为无效(注: 标记之前检查ip池总数是否低于阈值， 阈值=vps机器数 * 1/2,如果达到最小阈值，则不能标记为无效，只能降低优先级，避免ip池全部标记为无效ip，没有可用ip，或者可用ip越来越少，导致所有的请求压力都来到了剩下的ip，然后剩下的ip又抗不住，然后接着雪崩)
     * 2.守护线程进行ip检活（注：三次尝试连接ip都连接不上），将失联的ip标记为无效
     */
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

    /**
     * 代理ip是否存活
     *
     * @param proxyIp
     * @param maxRetry
     * @param checkUrl
     * @return
     */
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
    boolean isVpsInstanceAlive(VpsInstance vi, int maxRetry, String checkUrl) {
        if (vi == null) {
            return false;
        }
        int retry = 0;
        Session session = JschUtil.getSession(vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
        if (session == null) {
            log.error("session is null, quit, vps={}", vi.getIpAddrUsernamePwd());
            return false;
        }
        while (retry <= maxRetry) {
            retry++;
            Response response = null;
            try {
//                // tinyproxy 是否存活 后续改为nginx可用
//                String tinyproxyAlive = JschUtil.exec(session, VpsConfig.Operate.tinyproxy_alive.getCommand(), CharsetUtil.CHARSET_UTF_8);
//                if (StringUtils.isBlank(tinyproxyAlive)) {
//                    log.error("检活vps失败, tinyproxy不存在, vps={}, retry={}", vi.getInstanceInfo(), retry);
//                    // 重启tinyproxy
//                    JschUtil.exec(session, VpsConfig.Operate.restart_tinyproxy.getCommand(), CharsetUtil.CHARSET_UTF_8);
//                    // 再次检测
//                    String tinyproxyAlive2 = JschUtil.exec(session, VpsConfig.Operate.tinyproxy_alive.getCommand(), CharsetUtil.CHARSET_UTF_8);
//                    log.info("tinyproxy是否存活: {}, vps={}", StringUtils.isNoneBlank(tinyproxyAlive2), tinyproxyAlive2);
//                    continue;
//                }
                // ppp0 网卡的代理ip是否存在
                String proxyIp = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8).replaceAll("\n", "");
                if (StringUtils.isBlank(proxyIp)) {
                    log.error("检活vps失败, 代理ip不存在, vps={}, retry={}", vi.getInstanceInfo(), retry);
                    continue;
                }
                // 代理ip是否可用
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

    /**
     * 定时重播
     */
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
        proxyIp.getValid().set(false);
        addwaitingReplayVpsQueue(proxyIp);
    }

    /**
     * 解决长时间未释放连接的ip(超过60s)，直接将状态标记为false, 连接数设置为0
     */
    void releaseProxyIpWhenWaitTimeThreshold() {
        if (waitingReplayVpsQueue.isEmpty()) {
            log.info("waitingReplayVpsQueue is empty, quit");
            return;
        }
        //log.info("等待队列ip：{}", getIpPoolSimpleInfo(waitingReplayVpsQueue));
    }

    /**
     * 重播VPS
     * 安全重播的条件：
     * 1.ip被标记无效时, 并且等待ip未处理完的连接数处理完成（但不能超过最大时间30s），只有正在处理的连接数变为0，才能从ip池中移除，并且重播vps
     * 2.需要主动重播更换ip时
     */
//    void replayVps() {
//        if (waitingReplayVpsQueue.isEmpty()) {
//            log.info("等待重播vps队列为空, quit");
//            return;
//        }
//        try {
//            log.info("待重播ip数={}, data={}", waitingReplayVpsQueue.size(), getIpPoolSimpleInfo(waitingReplayVpsQueue));
//            // 筛选出连接数为0的代理ip
//            List<ProxyIp> collect = getRealReplayIp();
//            List<String> real = collect.stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
//            log.info("实际待重播ip数={}, data={}", collect.size(), JSON.toJSONString(real));
//            if (collect.isEmpty()) {
//                log.info("待重播vps数为0, quit");
//                return;
//            }
//            CountDownLatch countDownLatch = new CountDownLatch(collect.size());
//            int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
//            for (ProxyIp proxyIp : collect) {
//                replayVpsThreadPool.submit(() -> {
//                    try {
//                        VpsInstance vpsInstance = proxyIp.getVpsInstance();
//                        int retry = 1;
//                        while (retry <= maxRetryForCheckVpsAlive) {
//                            boolean changeVpsStatus = restartVps(vpsInstance);
//                            log.info("重播vps, 是否成功={}, vps={}", changeVpsStatus, vpsInstance.getIpAddr());
//                            if (changeVpsStatus) {
//                                // 重播成功 放入ip池
//                                addIpPool(vpsInstance);
//                                return;
//                            }
//                            retry++;
//                        }
//                        addwaitingReplayVpsQueue(proxyIp);
//                    } catch (Exception e) {
//                        log.error("重播vps异常, cause={}", e.getMessage(), e);
//                    } finally {
//                        countDownLatch.countDown();
//                    }
//                });
//            }
//            countDownLatch.await();
//            //List<String> collect2 = getRealReplayIp().stream().map(ProxyIp::getIpAddrWithTimeAndValid).distinct().collect(Collectors.toList());
//            //log.info("重播完后, 待重播队列ip数={}, data={}", collect2.size(), JSON.toJSONString(collect2));
//            log.info("重播结束, 待重播队列ip数={}, data={}", waitingReplayVpsQueue.size(), getIpPoolSimpleInfo(waitingReplayVpsQueue));
//        } catch (Exception e) {
//            log.error("重播vps异常, cause={}", e.getMessage());
//        }
//    }

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
        for (ProxyIp oldIp : ipPool) {
            String oldVpsAddr = oldIp.getVpsInstance().getIpAddr();
            if (oldVpsAddr.equalsIgnoreCase(vpsAddr)) {
                // ip不同了，说明ip被重播了，使用最新的
                if (!oldIp.getIpAddr().equals(proxyIp.getIpAddr())) {
                    oldIp.getValid().set(false);
                    ipPool.offer(proxyIp);
                    log.info("加入ip池, oldIp={}, proxyIp={}, vps={}", oldIp.getIpAddrWithTimeAndValid(), proxyIp.getIpAddrWithTimeAndValid(), proxyIp.getVpsInstance().getIpAddr());
                } else {
                    log.info("ip池已存在, 放弃添加, oldIp={}, proxyIp={}, vps={}", oldIp.getIpAddrWithTimeAndValid(), proxyIp.getIpAddrWithTimeAndValid(), proxyIp.getVpsInstance().getIpAddr());
                }
                break;
            }
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
        } else {
            log.info("加入待重播队列失败, 已存在, quit");
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
            } else {
                addwaitingReplayVpsQueue(poll);
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
//            // 检查tinyproxy存活
//            String tinyproxyAlive = JschUtil.exec(session, VpsConfig.Operate.tinyproxy_alive.getCommand(), CharsetUtil.CHARSET_UTF_8);
//            if (StringUtils.isBlank(tinyproxyAlive)) {
//                log.info("tinyproxy不存在, quit");
//                return null;
//            }
//            String oldIp = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
//            if (StringUtils.isNotBlank(oldIp)) {
//                log.info("vps={}, oldIp: {}", vi.getIpAddr(), oldIp.replaceAll("\n", ""));
//                return oldIp;
//            }
//            // 不能获取代理ip
//            log.info("oldIp获取失败, vps={}", vi.getIpAddr());
            // 旧ip不存在了，有两种情况：1.机器宕机了或者没有重拨网卡 2.tinyproxy进程下线了
            String tinyproxyPid = JschUtil.exec(session, VpsConfig.Operate.tinyproxy_alive.getCommand(), CharsetUtil.CHARSET_UTF_8);
            if (StringUtils.isBlank(tinyproxyPid)) {
                // tinyproxy 下线了，重启
                String restartTinyproxy = JschUtil.exec(session, VpsConfig.Operate.restart_tinyproxy.getCommand(), CharsetUtil.CHARSET_UTF_8);
                log.info("tinyproxy下线, 重启tinyproxy, vps={}, exec={}", vi.getIpAddr(), restartTinyproxy);
                // 不能在这递归获取ip, 防止一直不成功导致死循环
                return null;
            } else {
                log.error("tinyproxy存活, Pid={}, vps={}", tinyproxyPid.replaceAll("\n", ""), vi.getIpAddr());
                String oldIp = JschUtil.exec(session, VpsConfig.Operate.ifconfig.getCommand(), CharsetUtil.CHARSET_UTF_8);
                if (StringUtils.isNotBlank(oldIp)) {
                    log.info("vps={}, oldIp: {}", vi.getIpAddr(), oldIp.replaceAll("\n", ""));
                    return oldIp;
                } else {
                    log.info("tinyproxy存活, 但是 oldIp 为空, 强制重播");
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
            }
        } catch (Exception e) {
            log.error("获取oldIp异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 每2分钟检查一次所有的vps机器，失联的发送告警邮件
     */
    void checkAllVps() {
        List<VpsInstance> vpsInstancesFromDb = getVpsInstancesFromDb();
        if (CollectionUtils.isEmpty(vpsInstancesFromDb)) {
            log.warn("vpsInstances is empty, quit");
            return;
        }
        List<String> detail = vpsInstancesFromDb.stream().map(VpsInstance::getInstanceInfo).collect(Collectors.toList());
        log.info("VPS机器数={}, detail={}", vpsInstancesFromDb.size(), JSON.toJSONString(detail));
        CountDownLatch countDownLatch = new CountDownLatch(vpsInstancesFromDb.size());
        int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
        CopyOnWriteArrayList<VpsInstance> errorVps = new CopyOnWriteArrayList<>();
        try {
            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
            assert defaultTunnel != null;
            String checkUrl = defaultTunnel.getType() == TunnelType.VPS_DOMESTIC.getId() ? HttpCons.OVERSEA_IP_ALIVE_CHECK_URL : HttpCons.DOMESTIC_IP_ALIVE_CHECK_URL;
            vpsInstancesFromDb.forEach(vi -> ipAliveCheckThreadPool.submit(() -> {
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
        markIpStatus();
        heartBeatCheck();
    }

    void heartBeatCheck() {
        log.info("隧道存活心跳检测...");
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        assert defaultTunnel != null;
        String checkUrl = defaultTunnel.getType() == TunnelType.VPS_DOMESTIC.getId() ? HttpCons.OVERSEA_IP_ALIVE_CHECK_URL : HttpCons.DOMESTIC_IP_ALIVE_CHECK_URL;
        String innerIp = defaultTunnel.getLocation();
        int port = defaultTunnel.getPort();
        String username = defaultTunnel.getUsername();
        String password = defaultTunnel.getPassword();
        Response response = null;
        try {
            response = OkHttpTool.doGetByProxyIp(checkUrl, innerIp, port, username, password, 5, 5, 5);
            String resp = Objects.requireNonNull(response.body()).string();
            int code = response.code();
            log.info("心跳检测结果: checkUrl={}, code={}", checkUrl, code);
        } catch (Exception e) {
            log.error("心跳检测异常, checkUrl={}, cause={}", checkUrl, e.getMessage());
        } finally {
            OkHttpTool.closeResponse(response);
        }
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
