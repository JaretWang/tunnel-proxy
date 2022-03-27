package com.dataeye.proxy.cons;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jaret
 * @date 2022/3/18 16:07
 * @description
 */
public class Global {

    /**
     * 真实ip和代理ip映射缓存关系表
     */
    public static ConcurrentHashMap<String, String> PROXY_IP_MAPPING = new ConcurrentHashMap<>();

}
