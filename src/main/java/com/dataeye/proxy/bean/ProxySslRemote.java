package com.dataeye.proxy.bean;

/**
 * @author jaret
 * @date 2022/3/18 16:27
 * @description
 */
public class ProxySslRemote extends ProxyRemote {

    ProxySslRemote(String remoteHost, int remotePort, String proxyUserName, String proxyPassword, ProxyListenType remoteListenType) {
        super(remoteHost, remotePort, proxyUserName, proxyPassword, remoteListenType);
    }

}
