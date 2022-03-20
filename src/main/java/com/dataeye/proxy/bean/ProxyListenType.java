package com.dataeye.proxy.bean;

import ch.qos.logback.core.net.ssl.SSL;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jaret
 * @date 2022/3/18 16:21
 * @description 代理类型
 */
public enum ProxyListenType {

    /**
     * SSL
     */
    SSL,
    /**
     * text/plain
     */
    PLAIN;

    ProxyListenType(){}

    /**
     * 获取监听类型
     * @param _listenType
     * @return
     */
    public static ProxyListenType fromString(String _listenType) {
        if (StringUtils.equals(_listenType, "ssl")) {
            return ProxyListenType.SSL;
        } else if (StringUtils.equals(_listenType, "plain")) {
            return ProxyListenType.PLAIN;
        } else {
            throw new RuntimeException("Unknown listen type");
        }
    }

}
