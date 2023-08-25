package com.dataeye.proxy.monitor;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.selector.zhima.ZhiMaOrdinaryIpSelector;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * IP监控
 * 1.监控ip池，剔除劣质ip
 *
 * @author jaret
 * @date 2022/9/1 15:25
 * @description
 */
@Slf4j
public class IpMonitor {


    @Autowired
    ZhiMaOrdinaryIpSelector ipSelector;
    @Autowired
    ProxyServerConfig proxyServerConfig;

    public static void error(RequestMonitor requestMonitor, CommonIpSelector commonIpSelector, String errorMsg) {
        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(errorMsg);
        invoke(requestMonitor, false, commonIpSelector);
    }

    public static void ok(RequestMonitor requestMonitor, CommonIpSelector commonIpSelector) {
        invoke(requestMonitor, true, commonIpSelector);
    }

    /**
     * 监控ip的使用成功失败次数
     *
     * @param requestMonitor 请求监控bean
     * @param ok             本地调用ip是否成功
     */
    public static void invoke(RequestMonitor requestMonitor, boolean ok, CommonIpSelector commonIpSelector) {
        if (requestMonitor == null) {
            log.error("requestMonitor is null");
            return;
        }
        String proxyAddr = requestMonitor.getProxyAddr();
        ConcurrentLinkedQueue<ProxyIp> ipPool = commonIpSelector.getIpPool();
        for (ProxyIp proxyIp : ipPool) {
            String ipAddr = proxyIp.getIpAddr();
            if (proxyAddr.equals(ipAddr)) {
                if (ok) {
                    proxyIp.getOkTimes().incrementAndGet();
                    proxyIp.getUseTimes().incrementAndGet();
                } else {
                    proxyIp.getErrorTimes().incrementAndGet();
                    proxyIp.getUseTimes().incrementAndGet();
                }
            }
        }
    }


}
