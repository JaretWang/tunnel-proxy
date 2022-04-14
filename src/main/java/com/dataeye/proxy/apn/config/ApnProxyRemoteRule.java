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

package com.dataeye.proxy.apn.config;

import java.util.List;

/**
 * @author jaret
 * @date 2022/4/14 10:42
 */
public class ApnProxyRemoteRule {
    private String remoteHost;
    private int remotePort;
    private String proxyUserName;
    private String proxyPassword;
    private ApnProxyListenType remoteListenType;
    private List<String> originalHostList;

    public final String getRemoteHost() {
        return remoteHost;
    }

    final void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public final int getRemotePort() {
        return remotePort;
    }

    final void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public final ApnProxyListenType getRemoteListenType() {
        return remoteListenType;
    }

    final void setRemoteListenType(ApnProxyListenType remoteListenType) {
        this.remoteListenType = remoteListenType;
    }

    public final List<String> getOriginalHostList() {
        return originalHostList;
    }

    final void setOriginalHostList(List<String> originalHostList) {
        this.originalHostList = originalHostList;
    }

    public String getProxyUserName() {
        return proxyUserName;
    }

    final void setProxyUserName(String proxyUserName) {
        this.proxyUserName = proxyUserName;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    final void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }
}
