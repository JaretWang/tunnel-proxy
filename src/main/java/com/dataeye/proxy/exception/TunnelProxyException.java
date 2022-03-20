package com.dataeye.proxy.exception;

/**
 * @author jaret
 * @date 2022/3/18 15:13
 * @description
 */
public class TunnelProxyException extends RuntimeException {

    public TunnelProxyException(String msg) {
        super(msg);
    }

    public TunnelProxyException(String message, Throwable cause) {
        super(message, cause);
    }

}
