package com.dataeye.proxy.config;

/**
 * @author jaret
 * @date 2022/3/29 16:52
 * @description
 */
public interface TunnelManageCons {

    /**
     * client想要使用的隧道类型：direct exclusive tunnel
     */
    String HEADER_TUNEL_TYPE = "tunnel_use_type";
    /**
     * client使用时的认证信息
     */
    String HEADER_TUNEL_AUTH = "tunnel_authentication";
}
