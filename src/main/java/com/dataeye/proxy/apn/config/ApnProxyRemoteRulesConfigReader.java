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


import com.dataeye.proxy.apn.exception.ApnProxyConfigException;
import nu.xom.Element;
import nu.xom.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.config.ApnProxyRemoteRulesConfigReader 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyRemoteRulesConfigReader extends ApnProxyAbstractXmlConfigReader {

    @Override
    protected void realReadProcess(Element rootElement) {
        Element remoteRulesElement = rootElement;

        Elements ruleElements = remoteRulesElement.getChildElements("rule");

        for (int i = 0; i < ruleElements.size(); i++) {
            ApnProxyRemoteRule apnProxyRemoteRule = new ApnProxyRemoteRule();

            Element ruleElement = ruleElements.get(i);

            Elements remoteHostElements = ruleElement.getChildElements("remote-host");
            if (remoteHostElements.size() != 1) {
                throw new ApnProxyConfigException("Wrong config for: remote-host");
            }
            String remoteHost = remoteHostElements.get(0).getValue();

            apnProxyRemoteRule.setRemoteHost(remoteHost);

            Elements remotePortElements = ruleElement.getChildElements("remote-port");
            if (remoteHostElements.size() != 1) {
                throw new ApnProxyConfigException("Wrong config for: remote-port");
            }
            int remotePort = -1;
            try {
                remotePort = Integer.parseInt(remotePortElements.get(0).getValue());
            } catch (NumberFormatException nfe) {
                throw new ApnProxyConfigException("Invalid format for: remote-port", nfe);
            }

            apnProxyRemoteRule.setRemotePort(remotePort);

            Elements proxyUserNameElements = ruleElement.getChildElements("proxy-username");
            if (proxyUserNameElements.size() == 1) {
                String proxyUserName = proxyUserNameElements.get(0).getValue();
                apnProxyRemoteRule.setProxyUserName(proxyUserName);
            }

            Elements proxyPasswordElements = ruleElement.getChildElements("proxy-password");
            if (proxyPasswordElements.size() == 1) {
                String proxyPassword = proxyPasswordElements.get(0).getValue();
                apnProxyRemoteRule.setProxyPassword(proxyPassword);
            }

            Elements remoteListenTypeElements = ruleElement
                    .getChildElements("remote-listen-type");
            if (remoteListenTypeElements.size() != 1) {
                throw new ApnProxyConfigException("Wrong config for: remote-listen-type");
            }
            String _remoteListenType = remoteListenTypeElements.get(0).getValue();
            ApnProxyListenType remoteListenType = ApnProxyListenType
                    .fromString(_remoteListenType);
            apnProxyRemoteRule.setRemoteListenType(remoteListenType);

            if (remoteListenType == ApnProxyListenType.SSL) {
                //ApnProxySSLContextFactory.createSSLContext(remoteHost, remotePort);
            }

            // simple key; ssl trust store

            Elements applyListElements = ruleElement.getChildElements("apply-list");
            if (applyListElements.size() == 1) {
                Elements originalHostElements = applyListElements.get(0).getChildElements(
                        "original-host");

                List<String> originalHostList = new ArrayList<String>();
                for (int j = 0; j < originalHostElements.size(); j++) {
                    String originalHost = originalHostElements.get(j).getValue();
                    originalHostList.add(originalHost);
                }
                apnProxyRemoteRule.setOriginalHostList(originalHostList);
            }

            ApnProxyConfig.getConfig().addRemoteRule(apnProxyRemoteRule);
        }
    }
}
