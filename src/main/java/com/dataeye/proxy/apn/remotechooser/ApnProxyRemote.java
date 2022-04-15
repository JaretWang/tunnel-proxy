/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.dataeye.proxy.apn.remotechooser;

import com.dataeye.proxy.apn.config.ApnProxyListenType;
import lombok.Builder;
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
