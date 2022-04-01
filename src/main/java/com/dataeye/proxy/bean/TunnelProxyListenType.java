package com.dataeye.proxy.bean;

import com.dataeye.proxy.exception.TunnelProxyConfigException;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jaret
 * @date 2022/3/25 17:46
 * @description
 */
public enum TunnelProxyListenType {

    /**
     * SSL
     */
    SSL,
    /**
     * 文本
     */
    PLAIN;

    public static TunnelProxyListenType fromString(String listenType) {
        if (StringUtils.equals(listenType, "ssl")) {
            return TunnelProxyListenType.SSL;
        } else if (StringUtils.equals(listenType, "plain")) {
            return TunnelProxyListenType.PLAIN;
        } else {
            throw new TunnelProxyConfigException("Unknown listen type");
        }
    }

}
