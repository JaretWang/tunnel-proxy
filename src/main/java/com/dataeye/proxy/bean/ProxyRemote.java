package com.dataeye.proxy.bean;

import lombok.Builder;
import lombok.Data;

/**
 * @author jaret
 * @date 2022/3/18 16:21
 * @description
 */
@Data
@Builder
public class ProxyRemote {

    private String remoteHost;
    private int remotePort;
    private String proxyUserName;
    private String proxyPassword;
    private ProxyListenType remoteListenType;

}
