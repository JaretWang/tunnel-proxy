package com.dataeye.proxy.utils;

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.apn.bean.IpMonitor;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.service.IpPoolScheduleService;
import com.dataeye.proxy.service.TunnelInitService;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
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
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(2,
            new ThreadPoolConfig.TunnelThreadFactory("ip-monitor-"), new ThreadPoolExecutor.AbortPolicy());
    private static final double IP_USE_SUCCESS_PERCENT = 95;

    @Resource
    IpPoolScheduleService ipPoolScheduleService;
    @Resource
    TunnelInitService tunnelInitService;

    public static void invoke(RequestMonitor requestMonitor, boolean ok, String handler) {
        invoke(false, requestMonitor, ok, handler);
    }

    /**
     * 监控ip的使用成功失败次数
     *
     * @param isInitAdd      是否初次分配ip
     * @param requestMonitor 请求监控bean
     * @param ok             本地调用ip是否成功
     * @param handler        调用此监控方法的处理器
     */
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
            ipMonitor.setTunnelName(requestMonitor.getTunnelName());
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

    /**
     * 获取百分比
     *
     * @param num1 除数
     * @param num2 被除数
     * @return
     */
    public static String getPercent(float num1, float num2) {
        if (num1 == 0 || num2 == 0) {
            return "0";
        }
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);
        float devide = num1 / num2;
        return numberFormat.format(devide * 100);
    }

    /**
     * ip监控列表，定时任务
     */
    @PostConstruct
    public void schedule() {
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(new GetIpUseTask(), 0, 2, TimeUnit.SECONDS);
    }

    /**
     * 从ip池移除高错误率的ip
     */
    private void removeHighErrorPercent(String ip, TunnelInstance tunnelInstance) {
        String tunnelName = tunnelInstance.getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
        if (proxyIpPool.containsKey(tunnelName)) {
            // 在ip池中剔除
            log.warn("ip {} 成功率低于 {}%, 即将从IP池中移除", ip, IP_USE_SUCCESS_PERCENT);
            String ipStr = ip.split(":")[0];
            int port = Integer.parseInt(ip.split(":")[1]);
            ConcurrentLinkedQueue<ProxyCfg> proxyCfgs = proxyIpPool.get(tunnelName);
            for (ProxyCfg item : proxyCfgs) {
                if (item.getHost().equals(ipStr) && item.getPort().equals(port)) {
                    proxyCfgs.remove(item);
                    // 并添加一个新IP
                    String ipTimeRecord = ip + "(" + item.getExpireTime() + ")";
                    log.info("成功移除ip={}, 并添加一个新IP", ipTimeRecord);
                    // 移除完之后，再添加一个
                    ipPoolScheduleService.checkBeforeUpdate(proxyCfgs, tunnelInstance);
                    return;
                }
            }
            log.warn("移除ip失败, ip池中不存在该ip={}", ip);
            return;
        }
        log.error("移除ip失败, 隧道 {} 不存在", tunnelName);
    }

    class GetIpUseTask implements Runnable {

        @Override
        public void run() {
            synchronized (this) {
                try {
                    if (IP_MONITOR_MAP.isEmpty()) {
                        log.error("ip监控列表为空");
                        return;
                    }
                    for (Map.Entry<String, IpMonitor> entry : IP_MONITOR_MAP.entrySet()) {
                        // IP:PORT
                        String ip = entry.getKey();
                        IpMonitor ipMonitor = entry.getValue();
                        String tunnelName = ipMonitor.getTunnelName();
                        TunnelInstance tunnelInstance = tunnelInitService.getTunnel(tunnelName);
                        if (tunnelInstance == null) {
                            log.error("未匹配隧道实例 [{}]", tunnelName);
                            IP_MONITOR_MAP.remove(ip);
                            continue;
                        }

                        // 过期了就移除监控记录
                        LocalDateTime monitorExpireTime = ipMonitor.getExpireTime();
                        LocalDateTime now = LocalDateTime.now();
                        if (now.isAfter(monitorExpireTime)) {
                            // 此处移除之后，可能再次拉取到相同的ip
                            log.info("IP={} 过期, 移除监控记录，有效时间={}, 当前={}", ip, monitorExpireTime, now);
//                            removeHighErrorPercent(tunnelName, ip, tunnelInstance);
                            IP_MONITOR_MAP.remove(ip);
                        }

                        // 计算成功率
                        AtomicLong useTimes = ipMonitor.getUseTimes();
                        AtomicLong errorTimes = ipMonitor.getErrorTimes();
                        float okTimes = useTimes.longValue() - errorTimes.longValue();
                        if (okTimes < 0) {
                            log.error("okTimes < 0，remove ip={} monitor detail", ip);
                            IP_MONITOR_MAP.remove(ip);
                            continue;
                        }
                        String percent = getPercent(okTimes, useTimes.longValue());
                        log.info("ip={}, expireTime={}, useTimes={}, errorTimes={}, success percent={}%",
                                ip, monitorExpireTime, useTimes, errorTimes, percent);

                        // 根据某个ip的使用失败情况，在ip池中剔除
                        double percentValue = Double.parseDouble(percent);
                        if (percentValue < IP_USE_SUCCESS_PERCENT) {
                            removeHighErrorPercent(ip, tunnelInstance);
                        }
                    }
                } catch (Throwable e) {
                    log.error("IpMonitorUtils error={}", e.getMessage());
                }
            }
        }
    }

}
