package com.dataeye.proxy.exception;

/**
 * @author jaret
 * @date 2022/3/25 17:47
 * @description
 */
public class ProxyTypeUnSupportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProxyTypeUnSupportException(String msg) {
        super(msg);
    }

    public ProxyTypeUnSupportException(String message, Throwable cause) {
        super(message, cause);
    }

}
