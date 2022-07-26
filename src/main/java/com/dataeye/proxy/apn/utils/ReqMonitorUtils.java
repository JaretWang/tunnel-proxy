package com.dataeye.proxy.apn.utils;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MapUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/4/18 10:07
 * @description 请求监控工具
 */
@Component
public class ReqMonitorUtils {

    public static final int ERROR_LIST_THRESHOLD = 200;
    public static final int CHECK_INTERVAL = 1;
    public static final TimeUnit CHECK_TIME_UNIT = TimeUnit.MINUTES;
    public static final AtomicInteger FETCH_IP_NUM_PER_UNIT = new AtomicInteger(0);
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ReqMonitorUtils");
    private static final Logger dynamicIpLogger = MyLogbackRollingFileUtil.getLogger("dynamic-adjust-ip");
    private static final AtomicLong OK_TIMES = new AtomicLong(0);
    private static final AtomicLong ERROR_TIMES = new AtomicLong(0);
    private static final AtomicLong COST_TOTAL = new AtomicLong(0);
    private static final AtomicLong REQ_SIZE = new AtomicLong(0);
    private static final AtomicLong RESP_SIZE = new AtomicLong(0);
    private static final ConcurrentHashMap<String, AtomicInteger> ERROR_LIST = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("req-monitor-"), new ThreadPoolExecutor.AbortPolicy());
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchService;
    @Autowired
    IpSelector ipSelector;
    @Resource
    TunnelInitService tunnelInitService;
    @Autowired
    ProxyServerConfig proxyServerConfig;

    public static void ok(RequestMonitor requestMonitor, String handler) {
        requestMonitor.setSuccess(true);
        cost(requestMonitor, handler);
    }

    public static void error(RequestMonitor requestMonitor, String handler, String errorMsg) {
        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(errorMsg);
        cost(requestMonitor, handler);
    }

    public static void cost(RequestMonitor requestMonitor, String handler) {
        if (requestMonitor == null) {
            logger.error("requestMonitor is null");
            return;
        }
        requestMonitor.setCost(System.currentTimeMillis() - requestMonitor.getBegin());
        logger.debug("{} ms, {}, {}, {}, {}, {}, {}, {}",
                requestMonitor.getCost(), requestMonitor.isSuccess(), requestMonitor.getTunnelName(), handler,
                requestMonitor.getProxyAddr(), requestMonitor.getFailReason(), requestMonitor.getRequestType(), requestMonitor.getTargetAddr());
        String failReason = requestMonitor.getFailReason();
        if (StringUtils.isNotBlank(failReason)) {
            // 释放内存,防止错误日志撑爆内存
            if (ERROR_LIST.size() >= ERROR_LIST_THRESHOLD) {
                logger.info("错误原因列表(提前打印), size={}, value={}", ERROR_LIST.size(), JSON.toJSONString(MapUtils.sort(ERROR_LIST, true)));
                ERROR_LIST.clear();
            }
            AtomicInteger errorCount = ERROR_LIST.putIfAbsent(failReason, new AtomicInteger(1));
            // null 表示之前不存在
            if (errorCount != null) {
                errorCount.incrementAndGet();
                ERROR_LIST.put(failReason, errorCount);
            }
        }
        // 不用加安全机制，因为在handler是线程安全的
        boolean success = requestMonitor.isSuccess();
        if (success) {
            OK_TIMES.incrementAndGet();
        } else {
            ERROR_TIMES.incrementAndGet();
        }
        COST_TOTAL.addAndGet(requestMonitor.getCost());
        REQ_SIZE.addAndGet(requestMonitor.getRequestSize().get());
        RESP_SIZE.addAndGet(requestMonitor.getReponseSize().get());
    }

    @PostConstruct
    public void schedule() {
        if (!proxyServerConfig.isEnable()) {
            return;
        }
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(this::reqMonitorTask, 0, CHECK_INTERVAL, CHECK_TIME_UNIT);
    }

    void reqMonitorTask() {
        try {
            long okVal = OK_TIMES.longValue();
            long errorVal = ERROR_TIMES.longValue();
            long total = ERROR_TIMES.addAndGet(okVal);
            double costAvg, reqSize, respSize, reqBandwidth, respBandwidth;
            String percent;
            if (COST_TOTAL.get() == 0 || total == 0) {
                costAvg = 0;
                reqSize = 0;
                respSize = 0;
                reqBandwidth = 0;
                respBandwidth = 0;
                percent = "0";
                logger.error("数值统计异常, OK_TIMES={}, ERROR_TIMES={}, total={}", okVal, errorVal, total);
            } else {
                percent = IpMonitorUtils.getPercent(okVal, total);

                // cost
                BigDecimal cost = new BigDecimal(COST_TOTAL.get());
                BigDecimal reqTotal = new BigDecimal(total);
                costAvg = cost.divide(reqTotal, 2, BigDecimal.ROUND_HALF_UP).doubleValue();

                BigDecimal reqBytes = new BigDecimal(REQ_SIZE.get());
                BigDecimal respBytes = new BigDecimal(RESP_SIZE.get());
                BigDecimal unit1 = new BigDecimal(1024 * total);
                BigDecimal unit2 = new BigDecimal(1024 * CHECK_INTERVAL * 60);

                // avg_req_size
                reqSize = reqBytes.divide(unit1, 0, BigDecimal.ROUND_HALF_UP).doubleValue();
                // avg_resp_size
                respSize = respBytes.divide(unit1, 0, BigDecimal.ROUND_HALF_UP).doubleValue();
                // avg_req_bandwidth
                reqBandwidth = reqBytes.divide(unit2, 0, BigDecimal.ROUND_HALF_UP).doubleValue();
                // avg_resp_bandwidth
                respBandwidth = respBytes.divide(unit2, 0, BigDecimal.ROUND_HALF_UP).doubleValue();
            }

            logger.info("{} min, total={}, ok={}, error={}, ok_percent={}%，cost={} ms, req_size={} kb, resp_size={} kb, req_bandwidth={} kb/s, resp_bandwidth={} kb/s",
                    CHECK_INTERVAL, total, okVal, errorVal, percent, costAvg, reqSize, respSize, reqBandwidth, respBandwidth);
            logger.info("错误原因列表, size={}, value={}", ERROR_LIST.size(), JSON.toJSONString(MapUtils.sort(ERROR_LIST, true)));

            // 动态调整ip数,保证成功率
            dynamicAdjustIpPool(dynamicIpLogger, percent, CHECK_INTERVAL, CHECK_TIME_UNIT, costAvg);
        } catch (Throwable e) {
            logger.error("ReqMonitorUtils error={}", e.getMessage());
        } finally {
            // 重置
            OK_TIMES.set(0);
            ERROR_TIMES.set(0);
            COST_TOTAL.set(0);
            REQ_SIZE.set(0);
            RESP_SIZE.set(0);
            ERROR_LIST.clear();
            FETCH_IP_NUM_PER_UNIT.set(0);
        }
    }

    /**
     * 在有限的ip数量之内, 通过动态调整ip池的大小, 尽可能用最少的ip达到更高的成功率
     * <p>
     * 把一个大问题化为很多个小问题,再对每个小题求最优解,并保存历史记录,作为下一次计算最优解的数据来源,并再次求得最优解,如此循环往复,就求得了这个大问题的最优解
     * 动态规划解决方案:
     * 自上而下：你从最顶端开始不断地分解问题，直到你看到问题已经分解到最小并已得到解决，之后只用返回保存的答案即可。这叫做记忆存储。
     * 自下而上：你可以直接开始解决较小的子问题，从而获得最好的解决方案。在此过程中，你需要保证在解决问题之前先解决子问题。这可以称为表格填充算法。
     *
     * @param realSuccessPercent 真实成功率
     * @param checkInterval      检查时间间隔,单位:秒
     * @throws InterruptedException
     */
    public void dynamicAdjustIpPool(Logger logger, String realSuccessPercent, int checkInterval, TimeUnit unit, double costAvg) throws InterruptedException {
        if (StringUtils.isBlank(realSuccessPercent)) {
            logger.error("realSuccessPercent is empty, quit");
            return;
        }
        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        if (defaultTunnel == null) {
            logger.error("放弃动态调整: 隧道为空");
            return;
        }

        String alias = defaultTunnel.getAlias();
        // 单位时间内最多能使用的ip数
        int availableIpPerUnitTime = ipSelector.getAvailableIpPerUnitTime(logger, checkInterval, unit, defaultTunnel);
        if (availableIpPerUnitTime <= 0) {
            logger.error("放弃动态调整: 没有可用ip");
            return;
        }
        int coreIpSize = ipSelector.getCoreIpSize(defaultTunnel, availableIpPerUnitTime);
        if (coreIpSize <= 0) {
            logger.error("放弃动态调整: 核心ip数小于等于0");
            return;
        }
        if (FETCH_IP_NUM_PER_UNIT.get() >= availableIpPerUnitTime) {
            logger.warn("放弃动态调整: 单位时间内拉取的ip数={}, 阈值={}, 大于等于阈值", FETCH_IP_NUM_PER_UNIT.get(), availableIpPerUnitTime);
            return;
        }
        // 保证该隧道真实有被使用
        int minSuccessPercentForRemoveIp = defaultTunnel.getMinSuccessPercentForRemoveIp();
        if (minSuccessPercentForRemoveIp <= 0) {
            logger.error("放弃动态调整: 参数检查异常, coreIpSize={}, availableIp={}, minSuccessPercent={}%, realSuccessPercent={}%",
                    coreIpSize, availableIpPerUnitTime, minSuccessPercentForRemoveIp, realSuccessPercent);
            return;
        }
        double realPercent = Double.parseDouble(realSuccessPercent);
        if (realPercent <= 0) {
            logger.error("放弃动态调整: 真实请求成功率小于等于0");
            return;
        }
        ConcurrentLinkedQueue<ProxyIp> proxyIpPool = ipSelector.getProxyIpPool().get(alias);
        if (proxyIpPool == null || proxyIpPool.isEmpty()) {
            logger.error("放弃动态调整: tunnel={}, ip池为空", alias);
            return;
        }
        // 真实成功率 < 规定成功率,追加ip,保证成功率
        if (realPercent < minSuccessPercentForRemoveIp) {
            // 不能用ip池数量作为判断依据,因为是动态变化的,可能一段时间过期大量ip,导致判断小于可用ip数,然后一直追加
//            if (proxyIpPool.size() < availableIpPerUnitTime) {
            if (FETCH_IP_NUM_PER_UNIT.get() < availableIpPerUnitTime) {
                boolean status = ipSelector.addFixedIp("ip调整, 追加ip", proxyIpPool, defaultTunnel, 1, false);
                logger.info("追加ip, status={}, 真实成功率={}%, 规定成功率={}%, ip池大小={}, 单位时间内允许拉取的最大ip数={}",
                        status, realPercent, minSuccessPercentForRemoveIp, proxyIpPool.size(), availableIpPerUnitTime);
            } else {
                logger.warn("放弃动态调整: 真实成功率{}% < 规定成功率{}%, 但ip池数量{}大于等于单位时间内允许ip数{}",
                        realPercent, minSuccessPercentForRemoveIp, proxyIpPool.size(), availableIpPerUnitTime);
            }
        }
        // nothing to do
//        else {
//            // 真实成功率 >= 规定成功率,且百分比超过3个点,则减少ip
//            if ((realPercent - minSuccessPercentForRemoveIp) >= 3) {
//                // 即使减少也不能少于核心ip数
//                if (proxyIpPool.size() > coreIpSize) {
//                    boolean status = ipSelector.removeFixedIp(1, proxyIpPool);
//                    logger.info("减少ip, status={}, 真实成功率={}%, 规定成功率={}%, 真实百分比超过3个点, ip池大小={}, 单位时间内允许拉取的最小ip数={}",
//                            status, realPercent, minSuccessPercentForRemoveIp, proxyIpPool.size(), coreIpSize);
//                } else {
//                    logger.info("放弃动态调整: 真实成功率{}% >= 规定成功率{}%, 且百分比超过3个点, 但ip池数量{}小于等于单位时间内允许最小ip数{}",
//                            realPercent, minSuccessPercentForRemoveIp, proxyIpPool.size(), coreIpSize);
//                }
//            }
//        }

        int maxReqCostForAddIp = defaultTunnel.getMaxReqCostForAddIp();
        if (costAvg > maxReqCostForAddIp) {
            boolean status = ipSelector.addFixedIp("请求耗时大于 " + maxReqCostForAddIp + " ms, 追加ip", proxyIpPool, defaultTunnel, 1, false);
            logger.info("请求耗时大于 " + maxReqCostForAddIp + " ms, 追加ip, status={}, cost={} ms, ip池大小={}, 单位时间内允许拉取的最大ip数={}",
                    status, costAvg, proxyIpPool.size(), availableIpPerUnitTime);
        } else {
            logger.warn("放弃动态调整: costAvg={} ms", costAvg);
        }
    }

}
