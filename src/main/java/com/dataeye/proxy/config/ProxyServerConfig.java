package com.dataeye.proxy.config;

import com.dataeye.proxy.bean.ProxyListenType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/3/18 13:23
 * @description
 */
@Data
@Component
@ConfigurationProperties(prefix = "proxy.server")
public class ProxyServerConfig {

    /**
     * 代理server boss 线程数
     */
    private int bossThreadCount;
    /**
     * 代理server worker 线程数
     */
    private int workerThreadCount;
    /**
     * ip
     */
    private String host;
    /**
     * 端口
     */
    private int port;
    /**
     * 代理监听类型
     */
    private ProxyListenType listenType;
    /**
     * 签名文件路径
     */
    private String keyStorePath;
    /**
     * 签名文件密码
     */
    private String keyStroePassword;
    /**
     * 是否使用信任证书
     */
    private boolean useTrustStore = false;
    /**
     * 信任证书文件路径
     */
    private String trustStorePath;
    /**
     * 信任证书密码
     */
    private String trustStorePassword;
    /**
     * 第三方代理平台：直连ip访问链接
     */
    private String directIpAccessLink;
    /**
     * 第三方代理平台：独享ip访问链接
     */
    private String exclusiveIpAccessLink;
    /**
     * 第三方代理平台：隧道ip访问链接
     */
    private String tunnelIpAccessLink;
    /**
     * 隧道代理厂商地址
     */
    private String tunnelProxyManufacturerUrl;
    /**
     * 隧道代理用户名
     */
    private String tunnelProxyUsername;
    /**
     * 隧道代理密码
     */
    private String tunnelProxyPassword;
    /**
     * 隧道代理服务器地址
     */
    private String tunnelProxyServerIp;
    /**
     * 隧道代理服务器端口
     */
    private int tunnelProxyServerPort;

}
