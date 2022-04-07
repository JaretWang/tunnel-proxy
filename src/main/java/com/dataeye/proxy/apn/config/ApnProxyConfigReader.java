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
import com.dataeye.proxy.apn.exception.ApnProxyConfigException;
import nu.xom.Element;
import nu.xom.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.config.ApnProxyConfigReader 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyConfigReader extends ApnProxyAbstractXmlConfigReader {

    @SuppressWarnings("unused")
    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyServer");

    @Override
    protected void realReadProcess(Element rootElement) {
        Elements listenTypeElements = rootElement.getChildElements("listen-type");
        if (listenTypeElements.size() == 1) {
            String _listenType = listenTypeElements.get(0).getValue();
            ApnProxyConfig.getConfig().setListenType(ApnProxyListenType.fromString(_listenType));
        }

        Elements keyStoreElements = rootElement.getChildElements("key-store");
        if (keyStoreElements.size() == 1) {
            Elements keyStorePathElements = keyStoreElements.get(0).getChildElements("path");
            if (keyStorePathElements.size() == 1) {
                ApnProxyConfig.getConfig().setKeyStorePath(keyStorePathElements.get(0).getValue());
            }
            Elements keyStorePasswordElements = keyStoreElements.get(0)
                    .getChildElements("password");
            if (keyStorePasswordElements.size() == 1) {
                ApnProxyConfig.getConfig().setKeyStroePassword(
                        keyStorePasswordElements.get(0).getValue());
            }
        }

        Elements trustStoreElements = rootElement.getChildElements("trust-store");
        if (trustStoreElements.size() == 1) {
            ApnProxyConfig.getConfig().setUseTrustStore(true);
            Elements trustStorePathElements = trustStoreElements.get(0).getChildElements("path");
            if (trustStorePathElements.size() == 1) {
                ApnProxyConfig.getConfig().setTrustStorePath(
                        trustStorePathElements.get(0).getValue());
            }
            Elements trustStorePasswordElements = trustStoreElements.get(0).getChildElements(
                    "password");
            if (trustStorePasswordElements.size() == 1) {
                ApnProxyConfig.getConfig().setTrustStorePassword(
                        trustStorePasswordElements.get(0).getValue());
            }
        }

        Elements portElements = rootElement.getChildElements("port");
        if (portElements.size() == 1) {
            try {
                ApnProxyConfig.getConfig()
                        .setPort(Integer.parseInt(portElements.get(0).getValue()));
            } catch (NumberFormatException nfe) {
                throw new ApnProxyConfigException("Invalid format for: port", nfe);
            }
        }

        Elements threadCountElements = rootElement.getChildElements("thread-count");
        if (threadCountElements.size() == 1) {
            Elements bossElements = threadCountElements.get(0).getChildElements("boss");
            if (bossElements.size() == 1) {
                try {
                    ApnProxyConfig.getConfig().setBossThreadCount(
                            Integer.parseInt(bossElements.get(0).getValue()));
                } catch (NumberFormatException nfe) {
                    throw new ApnProxyConfigException("Invalid format for: boss", nfe);
                }
            }
            Elements workerElements = threadCountElements.get(0).getChildElements("worker");
            if (workerElements.size() == 1) {
                try {
                    ApnProxyConfig.getConfig().setWorkerThreadCount(
                            Integer.parseInt(workerElements.get(0).getValue()));
                } catch (NumberFormatException nfe) {
                    throw new ApnProxyConfigException("Invalid format for: worker", nfe);
                }
            }
        }

        Elements pacHostElements = rootElement.getChildElements("pac-host");
        if (pacHostElements.size() == 1) {
            ApnProxyConfig.getConfig().setPacHost(pacHostElements.get(0).getValue());
        }

        Elements useIpv6Elements = rootElement.getChildElements("use-ipv6");
        if (useIpv6Elements.size() == 1) {
            ApnProxyConfig.getConfig().setUseIpV6(
                    Boolean.parseBoolean(useIpv6Elements.get(0).getValue()));
        }

        Elements localIpRulesElements = rootElement.getChildElements("local-ip-rules");
        if (localIpRulesElements.size() == 1) {
            Elements ruleElements = localIpRulesElements.get(0).getChildElements("rule");

            for (int i = 0; i < ruleElements.size(); i++) {
                ApnProxyLocalIpRule apnProxyLocalIpRule = new ApnProxyLocalIpRule();

                Element ruleElement = ruleElements.get(i);

                Elements localIpElements = ruleElement.getChildElements("local-ip");
                if (localIpElements.size() != 1) {
                    throw new ApnProxyConfigException("Wrong config for: local-ip");
                }
                String localIp = localIpElements.get(0).getValue();

                apnProxyLocalIpRule.setLocalIp(localIp);

                Elements applyListElements = ruleElement.getChildElements("apply-list");
                if (applyListElements.size() == 1) {
                    Elements originalHostElements = applyListElements.get(0).getChildElements(
                            "original-host");

                    List<String> originalHostList = new ArrayList<String>();
                    for (int j = 0; j < originalHostElements.size(); j++) {
                        String originalHost = originalHostElements.get(j).getValue();
                        originalHostList.add(originalHost);
                    }
                    apnProxyLocalIpRule.setOriginalHostList(originalHostList);
                }

                ApnProxyConfig.getConfig().addLocalIpRuleList(apnProxyLocalIpRule);
            }

        }
    }

}
