package com.dataeye.proxy.cons;

import io.netty.util.AttributeKey;

/**
 * @author jaret
 * @date 2022/3/25 19:15
 * @description
 */
public class ProxyConstants {

    public static final AttributeKey<String> REQUST_URL_ATTRIBUTE_KEY = AttributeKey.valueOf("tunnel_proxy_request_url");
    public static final String CACHE_DIR = "cache";
    public static final String CACHE_DATA_DIR = "data";

}
