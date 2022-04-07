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

import com.dataeye.proxy.apn.config.ApnProxyConfig;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.config.ApnProxyRemoteRule;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser 14-1-8 16:13 (xmx) Exp $
 */
@Component
public class ApnProxyRemoteChooser11 {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(ApnProxyRemoteChooser11.class);

    private static final Logger remoteChooseLogger = LoggerFactory.getLogger("REMOTE_CHOOSE_LOGGER");

    public static final String proxyIp = "tps582.kdlapi.com";
    public static final int proxyPort = 15818;
    public static final String username = "t14480740933876";
    public static final String password = "wnwx5oeo";

    public static ApnProxyRemote chooseRemoteAddr(String originalHost, int originalPort) {
        ApnProxyRemote apRemote = null;

        ApnProxyRemoteRule remoteRule = getApplyRemoteRule(originalHost);
//        if (remoteRule != null) {
        if (true) {
            ApnProxyPlainRemote apPlainRemote = new ApnProxyPlainRemote();
            apPlainRemote.setAppleyRemoteRule(true);
//            apPlainRemote.setRemoteListenType(ApnProxyListenType.SSL);
            apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
            apPlainRemote.setRemoteHost(proxyIp);
            apPlainRemote.setRemotePort(proxyPort);
            apPlainRemote.setProxyUserName(username);
            apPlainRemote.setProxyPassword(password);
            apRemote = apPlainRemote;
            System.out.println("真实代理ip配置："+apPlainRemote.toString());


//            if (remoteRule.getRemoteListenType() == ApnProxyListenType.SSL) {
//                ApnProxySslRemote apSslRemote = new ApnProxySslRemote();
//                apSslRemote.setAppleyRemoteRule(true);
//                apSslRemote.setRemoteListenType(ApnProxyListenType.SSL);
//
//                apRemote = apSslRemote;
//            }
//
//            if (remoteRule.getRemoteListenType() == ApnProxyListenType.PLAIN) {
//                ApnProxyPlainRemote apPlainRemote = new ApnProxyPlainRemote();
//                apPlainRemote.setAppleyRemoteRule(true);
//                apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
//
//                apRemote = apPlainRemote;
//            }
//
//            apRemote.setRemoteHost(remoteRule.getRemoteHost());
//            apRemote.setRemotePort(remoteRule.getRemotePort());
//            apRemote.setProxyUserName(remoteRule.getProxyUserName());
//            apRemote.setProxyPassword(remoteRule.getProxyPassword());
        } else {
            apRemote = new ApnProxyPlainRemote();
            apRemote.setAppleyRemoteRule(false);
            apRemote.setRemoteHost(originalHost);
            apRemote.setRemotePort(originalPort);
            apRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
        }

        if (remoteChooseLogger.isInfoEnabled()) {
            remoteChooseLogger.info("Original host: " + originalHost + ", Original port: "
                    + originalPort + ", Remote: " + apRemote.getRemote()
                    + ", Remote type: " + apRemote.getRemoteListenType());
        }

        return apRemote;
    }

    private static ApnProxyRemoteRule getApplyRemoteRule(String host) {
        for (ApnProxyRemoteRule remoteRule : ApnProxyConfig.getConfig().getRemoteRuleList()) {
            for (String originalHost : remoteRule.getOriginalHostList()) {
                if (StringUtils.equals(originalHost, host)
                        || StringUtils.endsWith(host, "." + originalHost)) {
                    return remoteRule;
                }
            }
        }

        return null;
    }

}
