package com.dataeye.proxy.apn.utils;

import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final int interval = 5;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static void cost(RequestMonitor requestMonitor, String handler) {
        if (requestMonitor == null) {
            logger.error("requestMonitor is null");
            return;
        }
        requestMonitor.setCost(System.currentTimeMillis() - requestMonitor.getBegin());
        logger.info("{} ms, {}, {}, {}, {}, {}, {}, {}",
                requestMonitor.getCost(),
                handler,
                requestMonitor.isSuccess(),
                requestMonitor.getTunnelName(),
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
    }

    public static void cost(RequestMonitor requestMonitor) {
        cost(requestMonitor, "未知");
    }

    @PostConstruct
    public void schedule() {
        executorService.submit(new RequestUseMonitorTask());
    }

    static class RequestUseMonitorTask implements Runnable {

        @Override
        public void run() {
            while (true) {
                long okVal = OK_TIMES.longValue();
                long errorVal = ERROR_TIMES.longValue();
                long total = ERROR_TIMES.addAndGet(okVal);
                String percent = IpMonitorUtils.getPercent(okVal, total);
                logger.info("{} 分钟内, 请求总数={}, 成功={}, 失败={}, 成功率={}", interval, total, okVal, errorVal, percent);
                //重置
                OK_TIMES.set(0);
                ERROR_TIMES.set(0);
                try {
                    Thread.sleep(interval * 60 * 1000L);
                } catch (Throwable e) {
                    logger.info("异常: {}", e.getMessage());
                }
            }
        }
    }

}
