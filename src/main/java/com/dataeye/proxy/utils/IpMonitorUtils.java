package com.dataeye.proxy.utils;

import com.dataeye.proxy.apn.bean.IpMonitor;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/4/18 20:04
 * @description ip监控工具
 */
@Component
public class IpMonitorUtils {

    public static final ConcurrentHashMap<String, IpMonitor> IP_MONITOR_MAP = new ConcurrentHashMap<>();
    private static final Logger log = MyLogbackRollingFileUtil.getLogger("IpMonitorUtils");
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static void invoke(RequestMonitor requestMonitor, boolean ok, String handler) {
        invoke(false, requestMonitor, ok, handler);
    }

    public synchronized static void invoke(boolean isInitAdd, RequestMonitor requestMonitor, boolean ok, String handler) {
        if (requestMonitor == null) {
            log.error("requestMonitor is null");
            return;
        }
        log.debug("handler={}, 使用结果={}, 监控IP个数={}", handler, ok, IP_MONITOR_MAP.size());
        String proxyIp = requestMonitor.getProxyAddr();
        if (IP_MONITOR_MAP.containsKey(proxyIp)) {
            IpMonitor ipMonitor = IP_MONITOR_MAP.get(proxyIp);
            // 只能是请求结束后触发
            if (!isInitAdd) {
                if (ok) {
                    AtomicLong okTimes = ipMonitor.getOkTimes();
                    okTimes.incrementAndGet();
                } else {
                    AtomicLong errorTimes = ipMonitor.getErrorTimes();
                    errorTimes.incrementAndGet();
                }
            }

            // 只能再第一次添加触发
            if (isInitAdd) {
                // 使用次数
                AtomicLong useTimes = ipMonitor.getUseTimes();
                useTimes.incrementAndGet();
                // 请求报文的大小
                AtomicLong bandwidth = ipMonitor.getBandwidth();
                bandwidth.addAndGet(requestMonitor.getBandwidth() / 1024);
                ipMonitor.setExpireTime(requestMonitor.getExpireTime());
            }
            IP_MONITOR_MAP.putIfAbsent(proxyIp, ipMonitor);
        } else {
            IpMonitor ipMonitor = new IpMonitor();
            ipMonitor.setProxyIp(proxyIp);
            ipMonitor.setBandwidth(new AtomicLong(requestMonitor.getBandwidth() / 1024));
            if (ok) {
                ipMonitor.setOkTimes(new AtomicLong(1));
                ipMonitor.setErrorTimes(new AtomicLong(0));
            } else {
                ipMonitor.setOkTimes(new AtomicLong(0));
                ipMonitor.setErrorTimes(new AtomicLong(1));
            }
            ipMonitor.setUseTimes(new AtomicLong(1));
            ipMonitor.setExpireTime(requestMonitor.getExpireTime());
            IP_MONITOR_MAP.putIfAbsent(proxyIp, ipMonitor);
        }
    }

    public static String getPercent(float num1, float num2) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);
        float devide = num1 / num2;
        return numberFormat.format(devide * 100);
    }

    @PostConstruct
    public void schedule() {
        executorService.submit(new GetIpUseTask());
    }

    static class GetIpUseTask implements Runnable {

        @Override
        public void run() {
            while (true) {
                if (IP_MONITOR_MAP.isEmpty()) {
                    log.error("ip监控列表为空");
                }
                try {
                    for (Map.Entry<String, IpMonitor> entry : IP_MONITOR_MAP.entrySet()) {
                        String ip = entry.getKey();
                        IpMonitor ipMonitor = entry.getValue();

                        String proxyIp = ipMonitor.getProxyIp();
                        LocalDateTime expireTime = ipMonitor.getExpireTime();
                        AtomicLong useTimes = ipMonitor.getUseTimes();
                        AtomicLong errorTimes = ipMonitor.getErrorTimes();
                        float okTimes = useTimes.longValue() - errorTimes.longValue();
                        log.info("ip={}, expireTime={}, useTimes={}, errorTimes={}, success percent={}%",
                                proxyIp, expireTime, useTimes, errorTimes, getPercent(okTimes, useTimes.longValue()));
                        // 过期了就移除
                        LocalDateTime now = LocalDateTime.now();
                        if (now.isAfter(expireTime)) {
                            log.info("IP {} 过期,移除监控记录，有效时间={}, 当前={}", ip, expireTime, now);
                            IP_MONITOR_MAP.remove(ip);
                        }
                    }
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
