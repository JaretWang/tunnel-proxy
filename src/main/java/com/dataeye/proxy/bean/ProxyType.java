package com.dataeye.proxy.bean;

/**
 * @author jaret
 * @date 2022/3/29 11:35
 * @description
 */
public enum ProxyType {

    /**
     * 芝麻代理：直连ip
     */
    direct,
    /**
     * 芝麻代理：独享ip
     */
    exclusive,
    /**
     * 芝麻代理：隧道ip
     */
    tuunel,
    /**
     * 快代理：隧道代理
     */
    exclusiveTunnel;

}
