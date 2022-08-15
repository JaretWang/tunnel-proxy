package com.dataeye.proxy.server.remotechooser;

import com.dataeye.proxy.config.ApnProxyListenType;
import com.dataeye.proxy.utils.TimeUtils;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author jaret
 * @date 2022/4/14 10:39
 */
@Data
public abstract class ApnProxyRemote {
    private String remoteHost;
    private int remotePort;
    private String proxyUserName;
    private String proxyPassword;
    private LocalDateTime expireTime;
    private boolean appleyRemoteRule = false;
    private ApnProxyListenType remoteListenType;

    public final String getRemoteHost() {
        return remoteHost;
    }

    public final void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public final int getRemotePort() {
        return remotePort;
    }

    public final void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public final boolean isAppleyRemoteRule() {
        return appleyRemoteRule;
    }

    public final void setAppleyRemoteRule(boolean appleyRemoteRule) {
        this.appleyRemoteRule = appleyRemoteRule;
    }

    public final String getRemote() {
        String formatLocalDate = TimeUtils.formatLocalDate(expireTime);
        return this.remoteHost + ":" + this.remotePort+"("+formatLocalDate+")";
    }

    public final String getIpAddr() {
        return this.remoteHost + ":" + this.remotePort;
    }

    public ApnProxyListenType getRemoteListenType() {
        return remoteListenType;
    }

    public void setRemoteListenType(ApnProxyListenType remoteListenType) {
        this.remoteListenType = remoteListenType;
    }

    public String getProxyUserName() {
        return proxyUserName;
    }

    public void setProxyUserName(String proxyUserName) {
        this.proxyUserName = proxyUserName;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }
}
