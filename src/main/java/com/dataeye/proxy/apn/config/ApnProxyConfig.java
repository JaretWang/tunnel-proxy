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

import com.dataeye.logback.LogbackRollingFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.config.ApnProxyConfig 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyConfig {

    @SuppressWarnings("unused")
    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyConfig");

    private static ApnProxyConfig config = new ApnProxyConfig();

    private ApnProxyListenType listenType;

    private String keyStorePath;

    private String keyStroePassword;

    private boolean useTrustStore = false;

    private String trustStorePath;

    private String trustStorePassword;

    private int port;

    private int bossThreadCount;

    private int workerThreadCount;

    private String pacHost;

    private boolean useIpV6;

    private List<ApnProxyRemoteRule> remoteRuleList = new ArrayList<ApnProxyRemoteRule>();

    private List<ApnProxyLocalIpRule> localIpRuleList = new ArrayList<ApnProxyLocalIpRule>();

    private ApnProxyConfig() {
    }

    public final ApnProxyListenType getListenType() {
        return listenType;
    }

    final void setListenType(ApnProxyListenType listenType) {
        this.listenType = listenType;
    }

    public final String getKeyStorePath() {
        return keyStorePath;
    }

    final void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public final String getKeyStroePassword() {
        return keyStroePassword;
    }

    final void setKeyStroePassword(String keyStroePassword) {
        this.keyStroePassword = keyStroePassword;
    }

    public final boolean isUseTrustStore() {
        return useTrustStore;
    }

    final void setUseTrustStore(boolean useTrustStore) {
        this.useTrustStore = useTrustStore;
    }

    public final String getTrustStorePath() {
        return trustStorePath;
    }

    final void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public final String getTrustStorePassword() {
        return trustStorePassword;
    }

    final void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public final int getPort() {
        return port;
    }

    final void setPort(int port) {
        this.port = port;
    }

    public final int getBossThreadCount() {
        return bossThreadCount;
    }

    final void setBossThreadCount(int bossThreadCount) {
        this.bossThreadCount = bossThreadCount;
    }

    public final int getWorkerThreadCount() {
        return workerThreadCount;
    }

    final void setWorkerThreadCount(int workerThreadCount) {
        this.workerThreadCount = workerThreadCount;
    }

    public final String getPacHost() {
        return pacHost;
    }

    final void setPacHost(String pacHost) {
        this.pacHost = pacHost;
    }

    public final boolean isUseIpV6() {
        return useIpV6;
    }

    final void setUseIpV6(boolean useIpV6) {
        this.useIpV6 = useIpV6;
    }

    public final List<ApnProxyRemoteRule> getRemoteRuleList() {
        return remoteRuleList;
    }

    final void addRemoteRule(ApnProxyRemoteRule remoteRule) {
        this.remoteRuleList.add(remoteRule);
    }

    public final List<ApnProxyLocalIpRule> getLocalIpRuleList() {
        return localIpRuleList;
    }

    final void addLocalIpRuleList(ApnProxyLocalIpRule localIpRule) {
        this.localIpRuleList.add(localIpRule);
    }

    public static ApnProxyConfig getConfig() {
        return config;
    }

}
