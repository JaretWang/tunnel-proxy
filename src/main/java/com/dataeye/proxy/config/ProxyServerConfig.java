package com.dataeye.proxy.config;

import com.dataeye.proxy.bean.TunnelProxyListenType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
     * 本地代理服务器 ip
     */
    private String host;
    /**
     * 本地代理服务器 端口
     */
    private int port;
    /**
     * 第三方代理平台：直连ip访问链接
     */
    private String directIpAccessLink;
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
     * 签名文件路径
     */
    private String keyStorePath;
    /**
     * 签名文件密码
     */
    private String keyStorePassword;
    /**
     * 隧道代理：监听类型
     */
    private TunnelProxyListenType tunnelProxyListenType;
    /**
     * 代理商ip
     */
    private String remoteHost;
    /**
     * 代理商端口
     */
    private int remotePort;
    /**
     * 隧道代理：用户名
     */
    private String proxyUserName;
    /**
     * 隧道代理：密码
     */
    private String proxyPassword;
    /**
     * 是否使用代理商的代理发送请求
     */
    private boolean appleyRemoteRule = false;
    /**
     * 定时器的时间, 单位:秒
     */
    private int timerDuration;
    /**
     * 获取代理商的地址
     * @return
     */
    public final String getRemote() {
        return this.remoteHost + ":" + this.remotePort;
    }

}
