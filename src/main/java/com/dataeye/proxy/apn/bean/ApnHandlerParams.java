package com.dataeye.proxy.apn.bean;

import com.dataeye.proxy.apn.handler.ConcurrentLimitHandler;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
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
     * 代理ip抽取工具
     */
    ApnProxyRemoteChooser apnProxyRemoteChooser;
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

}
