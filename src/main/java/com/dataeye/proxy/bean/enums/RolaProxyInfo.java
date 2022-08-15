package com.dataeye.proxy.bean.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public enum RolaProxyInfo {
    /**
     * 中国服务器 http协议 (一般只用这个)
     */
    CHINA_HTTP("cn", "http/https", "gate9.rola.info", 1031),
    /**
     * 中国服务器 socks5协议 (一般只用这个)
     */
    CHINA_SOCKS5("cn", "socks5", "gate9.rola.info", 2031),
    /**
     * 新加坡服务器 http协议
     */
    SGP_HTTP("sg", "http/https", "proxysg.rola.info", 1000),
    /**
     * 新加坡服务器 socks5协议
     */
    SGP_SOCKS5("sg", "socks5", "proxysg.rola.info", 2000),
    /**
     * 美国服务器 http协议
     */
    US_HTTP("us", "http/https", "proxyus.rola.info", 1000),
    /**
     * 美国服务器 socks5协议
     */
    US_SOCKS5("us", "socks5", "proxyus.rola.info", 2000);

    @Getter
    String serverLocationCode;
    @Getter
    String protocol;
    @Getter
    String host;
    @Getter
    int port;
}

