package com.dataeye.proxy.apn.utils;

import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private static final int INTERVAL = 5;
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("req-monitor-"), new ThreadPoolExecutor.AbortPolicy());

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
        // 不用加安全机制，因为在handler是线程安全的
        boolean success = requestMonitor.isSuccess();
        if (success) {
            OK_TIMES.incrementAndGet();
        } else {
            ERROR_TIMES.incrementAndGet();
        }
        COST_TOTAL.addAndGet(requestMonitor.getCost());
    }

    public static void cost(RequestMonitor requestMonitor) {
        cost(requestMonitor, "未知");
    }

    @PostConstruct
    public void schedule() {
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(new RequestUseMonitorTask(), 0, INTERVAL, TimeUnit.MINUTES);
    }

    static class RequestUseMonitorTask implements Runnable {

        @Override
        public void run() {
            try {
                long okVal = OK_TIMES.longValue();
                long errorVal = ERROR_TIMES.longValue();
                long total = ERROR_TIMES.addAndGet(okVal);
                double costAvg;
                String percent;
                if (COST_TOTAL.get() == 0 || total == 0 || errorVal == 0) {
                    costAvg = 0;
                    percent = "0";
                    logger.error("数值统计异常, OK_TIMES={}, ERROR_TIMES={}, total={}", okVal, errorVal, total);
                } else {
                    BigDecimal cost = new BigDecimal(COST_TOTAL.get());
                    BigDecimal reqTotal = new BigDecimal(total);
                    costAvg = cost.divide(reqTotal, 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    percent = IpMonitorUtils.getPercent(okVal, total);
                }

                logger.info("{} 分钟内, 请求总数={}, 成功={}, 失败={}, 成功率={}%，平均耗时={} ms", INTERVAL, total, okVal, errorVal, percent, costAvg);
                //重置
                OK_TIMES.set(0);
                ERROR_TIMES.set(0);
                COST_TOTAL.set(0);
            } catch (Throwable e) {
                logger.error("ReqMonitorUtils error={}", e.getMessage());
            }
        }
    }

}
