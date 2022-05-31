package com.dataeye.proxy.apn.utils;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.service.IpPoolScheduleService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/4/18 10:07
 * @description 请求监控工具
 */
@Component
public class ReqMonitorUtils {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ReqMonitorUtils");
    private static final AtomicLong OK_TIMES = new AtomicLong(0);
    private static final AtomicLong ERROR_TIMES = new AtomicLong(0);
    private static final AtomicLong COST_TOTAL = new AtomicLong(0);
    private static final AtomicLong REQ_SIZE = new AtomicLong(0);
    private static final AtomicLong RESP_SIZE = new AtomicLong(0);
    private static final ConcurrentHashMap<String, Integer> ERROR_LIST = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> IP_CONNECT_TIME_OUT = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("req-monitor-"), new ThreadPoolExecutor.AbortPolicy());

    @Resource
    IpPoolScheduleService ipPoolScheduleService;
    @Resource
    TunnelInitService tunnelInitService;

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
        logger.info("{} ms, {}, {}, {}, {}, {}, {}, {}",
                requestMonitor.getCost(),
                requestMonitor.isSuccess(),
                requestMonitor.getTunnelName(),
                handler,
                requestMonitor.getProxyAddr(),
                requestMonitor.getFailReason(),
                requestMonitor.getRequestType(),
                requestMonitor.getTargetAddr());
        String failReason = requestMonitor.getFailReason();
        if (StringUtils.isNotBlank(failReason)) {
            Integer integer = ERROR_LIST.putIfAbsent(failReason, 1);
            // null 表示之前不存在
            if (integer != null) {
                ERROR_LIST.put(failReason, ++integer);
            }
            // 记录超时错误
            if (failReason.contains("Failed to connect to")) {
                String proxyAddr = requestMonitor.getProxyAddr();
                Integer count = IP_CONNECT_TIME_OUT.putIfAbsent(proxyAddr, 1);
                if (count != null) {
                    IP_CONNECT_TIME_OUT.put(proxyAddr, ++count);
                }
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

    /**
     * 检查ip连接超时错误失败次数，超过3次，剔除
     */
    void checkIpConnectTimeOutError() {
        try {
            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
            // 一段时间内，超时3次就剔除
            for (Map.Entry<String, Integer> entry : IP_CONNECT_TIME_OUT.entrySet()) {
                String ip = entry.getKey();
                Integer count = entry.getValue();
                int maxErrorCount = defaultTunnel.getRetryCount();
                if (count >= maxErrorCount) {
                    logger.warn("ip={} 连接超时次数={}, 大于 {} 次，即将移除", ip, count, maxErrorCount);
                    IpMonitorUtils.removeHighErrorPercent(ip, defaultTunnel, ipPoolScheduleService);
                    IP_CONNECT_TIME_OUT.remove(ip);
                }
            }
        } catch (Exception e) {
            logger.error("检查ip连接超时错误失败, 原因={}", e.getMessage());
        } finally {
            IP_CONNECT_TIME_OUT.clear();
        }
    }

    @PostConstruct
    public void schedule() {
        int reqMonitorCheckInterval = 5;
        int checkIpConnectTimeOutErrorInterval = 10;
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(() -> reqMonitorTask(reqMonitorCheckInterval), 0, reqMonitorCheckInterval, TimeUnit.MINUTES);
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(this::checkIpConnectTimeOutError, 0, checkIpConnectTimeOutErrorInterval, TimeUnit.SECONDS);
    }

    void reqMonitorTask(int checkInterval) {
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
                BigDecimal unit2 = new BigDecimal(1024 * checkInterval * 60);

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
                    checkInterval, total, okVal, errorVal, percent, costAvg, reqSize, respSize, reqBandwidth, respBandwidth);
            logger.info("错误原因列表, size={}, value={}", ERROR_LIST.size(), JSON.toJSONString(ERROR_LIST));

            //重置
            OK_TIMES.set(0);
            ERROR_TIMES.set(0);
            COST_TOTAL.set(0);
            REQ_SIZE.set(0);
            RESP_SIZE.set(0);
            ERROR_LIST.clear();
        } catch (Throwable e) {
            logger.error("ReqMonitorUtils error={}", e.getMessage());
        }
    }

}
