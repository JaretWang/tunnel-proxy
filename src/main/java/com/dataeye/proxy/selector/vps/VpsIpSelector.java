package com.dataeye.proxy.selector.vps;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.TunnelType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.dto.VpsInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.VpsConfig;
import com.dataeye.proxy.cons.HttpCons;
import com.dataeye.proxy.cons.Log;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.service.SendMailService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.VpsInstanceService;
import com.dataeye.proxy.service.impl.VpsFetchServiceImpl;
import com.dataeye.proxy.utils.CommandUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import com.jcraft.jsch.JSchException;
import lombok.Getter;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/11/7 14:24
 * @description vps ip 选择器
 */
@Component
public class VpsIpSelector implements CommonIpSelector {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("VpsFetchServiceImpl");
    private final ConcurrentLinkedQueue<ProxyIp> ipPool = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ProxyIp> waitingReplayVpsQueue = new ConcurrentLinkedQueue<>();
    //    private final ConcurrentLinkedQueue<ProxyIpTimer> waitingReplayVpsQueue = new ConcurrentLinkedQueue<>();
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

    public static void main(String[] args) throws JSchException {
        String oldIp = CommandUtils.exec(VpsConfig.Operate.ifconfig.getCommand(), "154.37.50.4", 20097, "root", "d5d4cc42d9");
        System.out.println(oldIp);
        String exec = CommandUtils.exec(VpsConfig.Operate.restart.getCommand(), "154.37.50.4", 20097, "root", "d5d4cc42d9");
        System.out.println(exec);
        String proxyIp = CommandUtils.exec(VpsConfig.Operate.ifconfig.getCommand(), "154.37.50.4", 20097, "root", "d5d4cc42d9");
        System.out.println(proxyIp);
    }

    @Override
    public void init() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS)) {
            return;
        }
        // 初始化ip池
        refreshVpsInstances();
        vpsFetchService.scheduleGetAllVpsIp();
        List<ProxyIp> ipList = vpsFetchService.getIpList();
        if (CollectionUtils.isEmpty(ipList)) {
            throw new RuntimeException("初始化ip池失败");
        }
        ipList.forEach(ipPool::offer);
        log.info("初始化ip池完成, data={}", JSON.toJSONString(ipPool));
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

    /**
     * 从数据库同步vps实例列表
     */
    @Scheduled(cron = "0/10 * * * * ?")
    void refreshVpsInstances() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS)) {
            return;
        }
        List<VpsInstance> collect = vpsInstanceService.list().stream()
                .filter(e -> Objects.nonNull(e) && e.getValid() == 1)
                .distinct()
                .collect(Collectors.toList());
        log.info("从数据库同步vps实例列表: {}", JSON.toJSONString(collect));
        vpsInstances = Optional.ofNullable(collect).orElse(new LinkedList<>());
    }

    /**
     * 标记ip状态
     * 1.成功率低于80%，标记为无效(注: 标记之前检查ip池总数是否低于阈值， 阈值=vps机器数 * 1/2,如果达到最小阈值，则不能标记为无效，只能降低优先级，避免ip池全部标记为无效ip，没有可用ip，或者可用ip越来越少，导致所有的请求压力都来到了剩下的ip，然后剩下的ip又抗不住，然后接着雪崩)
     * 2.守护线程进行ip检活（注：三次尝试连接ip都连接不上），将失联的ip标记为无效
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    void markIpStatus() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS)) {
            return;
        }
        try {
            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
            int minSuccessPercentForRemoveIp = defaultTunnel.getMinSuccessPercentForRemoveIp();
            int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
            CountDownLatch countDownLatch = new CountDownLatch(ipPool.size());
            for (ProxyIp proxyIp : ipPool) {
                markIpStatusThreadPool.submit(() -> {
                    try {
                        boolean check1 = checkIpSuccessPercent(proxyIp, minSuccessPercentForRemoveIp);
                        VpsInstance vpsInstance = proxyIp.getVpsInstance();
                        boolean check2 = checkVpsInstanceAlive(vpsInstance, maxRetryForCheckVpsAlive);
                        if (!check1 || !check2) {
                            log.info("标记ip无效状态, 成功率低于{}%={}, vps是否存活={}, proxyIp={}", check1, minSuccessPercentForRemoveIp, check2, JSON.toJSONString(proxyIp));
                            proxyIp.getValid().set(false);
                            if (vpsInstance != null) {
                                waitingReplayVpsQueue.offer(proxyIp);
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
     * 检查ip成功率是否低于阈值
     *
     * @param proxyIp 代理ip
     * @return
     */
    boolean checkIpSuccessPercent(ProxyIp proxyIp, double minSuccessPercent) {
        if (proxyIp == null) {
            return false;
        }
        AtomicBoolean valid = proxyIp.getValid();
        if (!valid.get()) {
            return false;
        }
        double successPercent = getSuccessPercent(proxyIp);
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
            Log.SERVER.error("total error, ProxyIp={}, total={}", JSON.toJSONString(proxyIp), total);
            return rate;
        }
        return new BigDecimal(okTimes.get()).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 检查单个vps实例是否存活
     *
     * @param vi       vps实例
     * @param maxRetry 最大重试次数
     * @return
     */
    boolean checkVpsInstanceAlive(VpsInstance vi, int maxRetry) {
        if (vi == null) {
            return false;
        }
        int retry = 1;
        while (retry <= maxRetry) {
            Response response = null;
            try {
                response = OkHttpTool.doGetByProxyIp(HttpCons.VPS_IP_ALIVE_CHECK_URL,
                        vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword(),
                        5, 5, 5);
                if (response.code() == 200) {
                    return true;
                } else {
                    retry++;
                }
            } catch (Exception e) {
                log.error("vps实例检活异常, vps={}, cause={}, retry={}", vi.getIp(), e.getMessage(), retry);
            } finally {
                OkHttpTool.closeResponse(response);
                log.info("vps={} 检测完成, retry={}", vi.getIp(), retry);
            }
        }
        return false;
    }

    @Scheduled(cron = "0 0/5 * * * ?")
    void fixedTimeReplay() {
        if (CollectionUtils.isEmpty(vpsInstances)) {
            log.error("定时重试失败, vpsInstances is empty, quit");
            return;
        }
        // 随机重播一个队列尾部ip (优化：按照成功率排序，重播成功率低的ip)，但vps有可能还在被使用，所以暂时先标记失效（意味着不再接收连接）
        Map<ProxyIp, Double> collect = ipPool.stream().sorted((o1, o2) -> {
            double successPercent1 = getSuccessPercent(o1);
            double successPercent2 = getSuccessPercent(o2);
            return (int) (successPercent1 - successPercent2);
        }).collect(Collectors.toMap(e -> e, this::getSuccessPercent, (e1, e2) -> e1));
        if (collect.isEmpty()) {
            log.error("经过排序后的代理IP为空");
            return;
        }
        log.error("按照成功率排序后, collect={}", JSON.toJSONString(collect));
        List<ProxyIp> proxyIpList = collect.keySet().stream().distinct().collect(Collectors.toList());
        ProxyIp proxyIp = proxyIpList.get(proxyIpList.size() - 1);
        proxyIp.getValid().set(false);
        waitingReplayVpsQueue.offer(proxyIp);
//        VpsInstance vpsInstance = proxyIp.getVpsInstance();
//        if (vpsInstance == null) {
//            log.error("ip池中的vps实例为空, proxyIp={}", JSON.toJSONString(proxyIp));
//            return;
//        }
//        boolean exec = restartVps(vpsInstance);
//        log.info("定时重播结果, result={}, vpsInstance={}", exec, JSON.toJSONString(vpsInstance));
    }

    /**
     * 解决长时间未释放连接的ip(超过60s)，直接将状态标记为false, 连接数设置为0
     */
    @Scheduled(cron = "0/5 * * * * ?")
    void releaseProxyIpWhenWaitTimeThreshold() {
        if (waitingReplayVpsQueue.isEmpty()) {
            log.info("waitingReplayVpsQueue is empty, quit");
            return;
        }
        for (ProxyIp proxyIp : waitingReplayVpsQueue) {
            log.info("等待队列ip：{}", JSON.toJSONString(proxyIp));
        }
    }

    /**
     * 重播VPS
     * 安全重播的条件：
     * 1.ip被标记无效时, 并且等待ip未处理完的连接数处理完成（但不能超过最大时间30s），只有正在处理的连接数变为0，才能从ip池中移除，并且重播vps
     * 2.需要主动重播更换ip时
     */
    @Scheduled(cron = "0/5 * * * * ?")
    void replayVps() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS)) {
            return;
        }
        if (waitingReplayVpsQueue.isEmpty()) {
            log.info("等待重播vps队列为空, quit");
            return;
        }
        try {
            log.info("待重播ip数={}, data={}", waitingReplayVpsQueue.size(), JSON.toJSONString(waitingReplayVpsQueue));
            // 筛选出连接数为0的代理ip
            List<ProxyIp> collect = waitingReplayVpsQueue.stream()
                    .filter(Objects::nonNull).filter(e -> !e.getValid().get() && e.getConnecting().get() == 0)
                    .distinct().collect(Collectors.toList());
            log.info("实际待重播ip数={}, data={}", collect.size(), JSON.toJSONString(collect));
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
                            log.info("重播vps, 是否成功={}, vps={}", changeVpsStatus, vpsInstance.getIp());
                            if (changeVpsStatus) {
                                return;
                            }
                            retry++;
                        }
                        // 重播失败的再次放入队列
                        waitingReplayVpsQueue.offer(proxyIp);
                    } catch (Exception e) {
                        log.error("重播vps异常, cause={}", e.getMessage(), e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }
            log.info("重播完后, 待重播队列ip数={}, data={}", waitingReplayVpsQueue.size(), JSON.toJSONString(waitingReplayVpsQueue));
            countDownLatch.await();
        } catch (Exception e) {
            log.error("重播vps异常, cause={}", e.getMessage());
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
        String type = VpsConfig.Operate.restart.getType();
        String command = VpsConfig.Operate.restart.getCommand();
        try {
            String oldIp = CommandUtils.exec(VpsConfig.Operate.ifconfig.getCommand(), vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
            if (StringUtils.isBlank(oldIp)) {
                log.info("oldIp获取失败，command={}, vps={}, oldIp={}", command, vi.getIp(), oldIp);
                return false;
            }
            String restart = CommandUtils.exec(command, vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
            String newIp = CommandUtils.exec(VpsConfig.Operate.ifconfig.getCommand(), vi.getIp(), vi.getPort(), vi.getUsername(), vi.getPassword());
            if (StringUtils.isBlank(newIp)) {
                log.info("newIp获取失败，command={}, vps={}, newIp={}", command, vi.getIp(), newIp);
                return false;
            }
            String oldIpStr = oldIp.split("\n")[0];
            String newIpStr = newIp.split("\n")[0];
            if (oldIpStr.equals(newIpStr)) {
                log.info("执行失败, type={}, command={}, vps={}, oldIp={}, newIp={}, restart={}", type, command, vi.getIp(), oldIp, newIp, restart);
                return false;
            }
            log.info("执行成功, type={}, command={}, vps={}, oldIp={}, newIp={}, restart={}", type, command, vi.getIp(), oldIpStr, newIpStr, restart);
            return true;
        } catch (Exception e) {
            log.error("执行异常, type={}, command={}, vps={}, cause={}", type, command, JSON.toJSONString(vi), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 每2分钟检查一次所有的vps机器
     */
    @Scheduled(cron = "0 0/2 * * * ?")
    void checkAllVps() {
        if (!isStart(tunnelInitService, proxyServerConfig, TunnelType.VPS)) {
            return;
        }
        if (CollectionUtils.isEmpty(vpsInstances)) {
            log.warn("vpsInstances is empty, quit");
            return;
        }
        log.info("VPS机器数={}, detail={}", vpsInstances.size(), JSON.toJSONString(vpsInstances));
        CopyOnWriteArrayList<VpsInstance> errorInstance = new CopyOnWriteArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(vpsInstances.size());
        int maxRetryForCheckVpsAlive = vpsConfig.getMaxRetryForCheckVpsAlive();
        try {
            vpsInstances.forEach(vi -> ipAliveCheckThreadPool.submit(() -> {
                try {
                    if (!checkVpsInstanceAlive(vi, maxRetryForCheckVpsAlive)) {
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
                List<VpsInstance> distinct = errorInstance.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
                log.error("失联机器: {}", JSON.toJSONString(distinct));
                String subject = "VPS心跳检测告警, 失联机器数量: " + distinct.size();
                String content = "失联机器: " + System.lineSeparator() + JSON.toJSONString(distinct);
                int sendAlarmEmail = tunnelInitService.getDefaultTunnel().getSendAlarmEmail();
                if (sendAlarmEmail == 1) {
                    sendMailService.sendMail(subject, content);
                }
            }
        } catch (Exception e) {
            log.info("检查所有vps机器存活状态异常, cause={}", e.getMessage(), e);
        }
    }

}
