package com.dataeye.proxy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.lang.String;

/**
 * @author jaret
 * @date 2022/3/18 13:23
 * @description
 */
@Data
@Builder
@Component
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "tunnel.manage")
public class TunnelManageConfig {
    /**
     * 连接隧道的用户名
     */
    String authUsername;
    /**
     * 连接隧道的密码
     */
    String authPassword;
    /**
     * 初始化的创建的隧道数量
     */
    int initTunnelCount;
    /**
     * 业务线程池数量
     */
    int businessThreadPoolSize;
    /**
     * 每个隧道初始化拥有的代理ip数量
     */
    int ipCountEachTunnel;
    /**
     * 代理ip池自动增加ip的阈值因子
     */
    double loadFactor;
    /**
     * 并发请求的最大数量
     */
    int concurrentRequestSize;
    /**
     * 隧道最大带宽限制，单位：MB
     */
    int maxBandWidth;
    /**
     * netty proxy client 连接超时时间
     */
    int connectTimeoutMillis;
    /**
     * 每种代理IP的，初始IP池的大小，所有server共享一个ip池
     */
    int sharedProxyIpPoolSizeEachType;
}
