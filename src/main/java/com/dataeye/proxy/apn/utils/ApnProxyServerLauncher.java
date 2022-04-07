///*
// * Copyright (c) 2014 The APN-PROXY Project
// *
// * The APN-PROXY Project licenses this file to you under the Apache License,
// * version 2.0 (the "License"); you may not use this file except in compliance
// * with the License. You may obtain a copy of the License at:
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// * License for the specific language governing permissions and limitations
// * under the License.
// */
//
//package com.dataeye.proxy.apn;
//
//import com.dataeye.proxy.apn.config.ApnProxyConfig;
//import com.dataeye.proxy.apn.config.ApnProxyConfigReader;
//import com.dataeye.proxy.apn.config.ApnProxyRemoteRulesConfigReader;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.apache.log4j.xml.DOMConfigurator;
//
//import javax.xml.parsers.FactoryConfigurationError;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.net.MalformedURLException;
//
///**
// * @author xmx
// * @version $Id: com.dataeye.proxy.apn.ApnProxyServerLauncher 14-1-8 16:13 (xmx) Exp $
// */
//public class ApnProxyServerLauncher {
//
//    private static final Logger logger = LogbackRollingFileUtil.getLogger(ApnProxyServerLauncher.class);
//
//    static {
//        File log4jConfigFile = new File(com.dataeye.proxy.apn.cons.ApnProxyConstants.LOG4J_CONFIG_FILE);
//        if (log4jConfigFile.exists()) {
//            try {
//                DOMConfigurator.configure(log4jConfigFile.toURI().toURL());
//            } catch (MalformedURLException e) {
//                System.err.println(e);
//            } catch (FactoryConfigurationError e) {
//                System.err.println(e);
//            }
//        }
//    }
//
//    public static void main(String[] args) {
//
//        try {
//            ApnProxyConfigReader reader = new ApnProxyConfigReader();
//            reader.read(new File(com.dataeye.proxy.apn.cons.ApnProxyConstants.CONFIG_FILE));
//        } catch (FileNotFoundException e) {
//            logger.error("The config file conf/config.xml not exists!");
//            System.exit(1);
//        }
//
//        try {
//            ApnProxyRemoteRulesConfigReader reader = new ApnProxyRemoteRulesConfigReader();
//            reader.read(new File(com.dataeye.proxy.apn.cons.ApnProxyConstants.REMOTE_RULES_CONFIG_FILE));
//        } catch (FileNotFoundException e) {
//            logger.warn("The config file conf/remote-rules.xml-- not exists, no remote rules configured!");
//        }
//
//        if (ApnProxyConfig.getConfig().isUseIpV6()) {
//            System.setProperty("java.net.preferIPv6Addresses", "true");
//        }
//
//        ApnProxyServer server = new ApnProxyServer();
//        server.start();
//
//    }
//}
