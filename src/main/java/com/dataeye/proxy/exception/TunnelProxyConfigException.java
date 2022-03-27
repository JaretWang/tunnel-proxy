package com.dataeye.proxy.exception;

/**
 * @author jaret
 * @date 2022/3/25 17:47
 * @description
 */
public class TunnelProxyConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TunnelProxyConfigException(String msg) {
        super(msg);
    }

    public TunnelProxyConfigException(String message, Throwable cause) {
        super(message, cause);
    }

}
