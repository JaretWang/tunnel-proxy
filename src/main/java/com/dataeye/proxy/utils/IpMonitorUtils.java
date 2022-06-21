package com.dataeye.proxy.utils;

import com.dataeye.proxy.apn.bean.IpMonitor;
import com.dataeye.proxy.apn.bean.ProxyIp;
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
    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("ip-monitor-"), new ThreadPoolExecutor.AbortPolicy());

    @Resource
    IpPoolScheduleService ipPoolScheduleService;
    @Resource
    TunnelInitService tunnelInitService;

    public static void error(RequestMonitor requestMonitor, String handler, String errorMsg) {
        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(errorMsg);
        invoke(false, requestMonitor, false, handler);
    }

    public static void ok(RequestMonitor requestMonitor, String handler) {
        invoke(false, requestMonitor, true, handler);
    }

    /**
     * 监控ip的使用成功失败次数
     *
     * @param isInitAdd      是否初次分配ip
     * @param requestMonitor 请求监控bean
     * @param ok             本地调用ip是否成功
     * @param handler        调用此监控方法的处理器
     */
    public static void invoke(boolean isInitAdd, RequestMonitor requestMonitor, boolean ok, String handler) {
        if (requestMonitor == null) {
            log.error("requestMonitor is null");
            return;
        }
        String proxyIp = requestMonitor.getProxyAddr();
        IpMonitor oldIpMonitor = IP_MONITOR_MAP.putIfAbsent(proxyIp, buildVal(requestMonitor, ok));
        if (oldIpMonitor != null) {
            // 只能在第一次初始化添加触发
            if (isInitAdd) {
                // 使用次数
                oldIpMonitor.getUseTimes().incrementAndGet();
                // 请求报文的大小
                oldIpMonitor.getBandwidth().addAndGet(requestMonitor.getBandwidth() / 1024);
                oldIpMonitor.setExpireTime(requestMonitor.getExpireTime());
            }

            // 只能是请求结束后触发
            if (!isInitAdd) {
                if (ok) {
                    oldIpMonitor.getOkTimes().incrementAndGet();
                } else {
                    oldIpMonitor.getErrorTimes().incrementAndGet();
                }
            }
            IP_MONITOR_MAP.put(proxyIp, oldIpMonitor);
        }
    }

    static IpMonitor buildVal(RequestMonitor requestMonitor, boolean ok) {
        IpMonitor ipMonitor = new IpMonitor();
        ipMonitor.setTunnelName(requestMonitor.getTunnelName());
        ipMonitor.setProxyIp(requestMonitor.getProxyAddr());
        ipMonitor.getBandwidth().addAndGet(requestMonitor.getBandwidth() / 1024);
        if (ok) {
            ipMonitor.getOkTimes().incrementAndGet();
        } else {
            ipMonitor.getErrorTimes().incrementAndGet();
        }
        ipMonitor.getUseTimes().incrementAndGet();
        ipMonitor.setExpireTime(requestMonitor.getExpireTime());
        return ipMonitor;
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
     * 从ip池移除高错误率的ip
     */
    public static void removeHighErrorPercent(String ip, TunnelInstance tunnelInstance, IpPoolScheduleService ipPoolScheduleService) throws InterruptedException {
        String tunnelName = tunnelInstance.getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
        if (proxyIpPool.containsKey(tunnelName)) {
            // 在ip池中剔除
            log.warn("ip={} 成功率低于 {}%, 即将从IP池中移除", ip, tunnelInstance.getMinSuccessPercentForRemoveIp());
            String ipStr = ip.split(":")[0];
            int port = Integer.parseInt(ip.split(":")[1]);
            ConcurrentLinkedQueue<ProxyIp> ipPool = proxyIpPool.get(tunnelName);
            for (ProxyIp item : ipPool) {
                if (item.getHost().equals(ipStr) && item.getPort().equals(port)) {
                    item.getValid().set(false);
                    String ipTimeRecord = ip + "(" + item.getExpireTime() + ")";
                    log.info("成功移除ip={}, 并添加一个新IP", ipTimeRecord);
                    // 移除完之后，再添加一个新 ip
                    ipPoolScheduleService.checkBeforeUpdate(ipPool, tunnelInstance, 1);
                    return;
                }
            }
            // ip池中不存在该ip,就应该移除对该ip的监控
            log.warn("移除ip失败, ip池中不存在该ip={}, 即将移除对该ip的监控记录", ip);
            return;
        }
        log.error("移除ip失败, 隧道 {} 不存在", tunnelName);
    }

    /**
     * ip监控列表，定时任务
     */
    @PostConstruct
    public void schedule() {
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(new IpUseMonitorTask(), 0, 2, TimeUnit.SECONDS);
    }

    class IpUseMonitorTask implements Runnable {

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
                        // 在一定数量下的请求成功率低于阈值时，需要从ip池钟剔除
                        // 隧道启动后，如果一直不用，监控工具不应该按照成功百分比剔除掉ip，因为ip的成功率都是0
                        double percentValue = Double.parseDouble(percent);
                        if (percentValue < tunnelInstance.getMinSuccessPercentForRemoveIp() && useTimes.intValue() >= tunnelInstance.getMinUseTimesForRemoveIp()) {
                            log.warn("剔除ip={}, 最低成功率限制={}%, 实际成功率={}%, 最小使用次数限制={}, 实际使用次数={}",
                                    ip, tunnelInstance.getMinSuccessPercentForRemoveIp(), percentValue, tunnelInstance.getMinUseTimesForRemoveIp(), useTimes.intValue());
                            removeHighErrorPercent(ip, tunnelInstance, ipPoolScheduleService);
                            // 移除监控记录
                            IP_MONITOR_MAP.remove(ip);
                        } else {
                            // 为了不让监控日志中看到被剔除的ip
                            log.info("ip={}, expireTime={}, useTimes={}, errorTimes={}, success percent={}%",
                                    ip, monitorExpireTime, useTimes, errorTimes, percent);
                        }
                    }
                } catch (Throwable e) {
                    log.error("IpMonitorUtils error={}", e.getMessage());
                }
            }
        }
    }

}
