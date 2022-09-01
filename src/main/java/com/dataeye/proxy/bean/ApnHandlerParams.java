package com.dataeye.proxy.bean;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.selector.CommonIpSelector;
import com.dataeye.proxy.server.handler.ConcurrentLimitHandler;
import com.dataeye.proxy.server.service.RequestDistributeService;
import com.dataeye.proxy.service.TunnelInitService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author jaret
 * @date 2022/4/7 13:32
 * @description
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApnHandlerParams {

    /**
     * 隧道实例
     */
    TunnelInstance tunnelInstance;
    /**
     * 请求分发服务
     */
    RequestDistributeService requestDistributeService;
    /**
     * 请求监控工具
     */
    RequestMonitor requestMonitor;
    /**
     *限流处理器
     */
    ConcurrentLimitHandler concurrentLimitHandler;
    /**
     * 带宽流量监控线程池
     */
    ScheduledThreadPoolExecutor trafficScheduledThreadPool;
    /**
     * 隧道配置参数初始化和更新服务
     */
    TunnelInitService tunnelInitService;
    /**
     * 代理服务配置
     */
    ProxyServerConfig proxyServerConfig;
    /**
     * 公共ip选择器
     */
    CommonIpSelector commonIpSelector;

//    @Scheduled(cron = "0/5 * * * * ?")
//    void update(){
//        if (tunnelInitService != null) {
//            TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
//            if (Objects.nonNull(tunnelInstance)) {
//                this.tunnelInstance = defaultTunnel;
//            }
//        }
//    }

}
