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

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.config.ApnProxyConfig;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.config.ApnProxyRemoteRule;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.IpPoolScheduleService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser 14-1-8 16:13 (xmx) Exp $
 */
@Component
public class ApnProxyRemoteChooser {

    @SuppressWarnings("unused")
    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyRemoteChooser");
//    private static final Logger remoteChooseLogger = LoggerFactory.getLogger("REMOTE_CHOOSE_LOGGER");

    @Autowired
    IpPoolScheduleService ipPoolScheduleService;
    private final AtomicInteger errorCount = new AtomicInteger(3);

//    public static String PROXY_IP = "tps582.kdlapi.com";
//    public static int PROXY_PORT = 15818;
//    public static String USERNAME = "t14480740933876";
//    public static String PASSWORD = "wnwx5oeo";

//    public static String PROXY_IP = "183.165.192.150";
//    public static int PROXY_PORT = 4215;
//    public static String USERNAME = "";
//    public static String PASSWORD = "";

    /**
     * 从代理ip池获取ip
     *
     * @param tunnelInstance
     * @return
     */
    public ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) {
        String proxyServer = tunnelInstance.getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
        ConcurrentLinkedQueue<ProxyCfg> proxyCfgsQueue = proxyIpPool.get(proxyServer);
        if (proxyCfgsQueue == null || proxyCfgsQueue.isEmpty()) {
            logger.error("实例 {} 对应的代理IP列表为空，需要重新加载", proxyServer);
//            ipPoolScheduleService.checkAndUpdateIp();
            ipPoolScheduleService.initSingleServer(tunnelInstance);
            errorCount.decrementAndGet();
            if (errorCount.get() <= 0) {
                logger.error("连续 3 次初始化ip池失败, errorCount: {}", errorCount.get());
                return null;
            }
            return getProxyConfig(tunnelInstance);
        } else {
            errorCount.set(3);
            ProxyCfg poll = proxyCfgsQueue.poll();
            if (Objects.nonNull(poll)) {
                logger.info("从队列中获取代理ip的结果：{}", poll);
                // 取了需要再放进去
                proxyCfgsQueue.offer(poll);
                ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
                apPlainRemote.setAppleyRemoteRule(true);
                apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
                apPlainRemote.setRemoteHost(poll.getHost());
                apPlainRemote.setRemotePort(poll.getPort());
                apPlainRemote.setProxyUserName(poll.getUserName());
                apPlainRemote.setProxyPassword(poll.getPassword());
                return apPlainRemote;
            }
            throw new RuntimeException("从队列中 poll 出来的ip为空");
        }
    }

//    public static ApnProxyRemote chooseRemoteAddr(String originalHost, int originalPort) {
//        ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
//        apPlainRemote.setAppleyRemoteRule(true);
////            apPlainRemote.setRemoteListenType(ApnProxyListenType.SSL);
//        apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
//        apPlainRemote.setRemoteHost(PROXY_IP);
//        apPlainRemote.setRemotePort(PROXY_PORT);
//        apPlainRemote.setProxyUserName(USERNAME);
//        apPlainRemote.setProxyPassword(PASSWORD);
//        System.out.println("真实代理ip配置：" + apPlainRemote.toString());
//        return apPlainRemote;
//    }
//
//    private static ApnProxyRemoteRule getApplyRemoteRule(String host) {
//        for (ApnProxyRemoteRule remoteRule : ApnProxyConfig.getConfig().getRemoteRuleList()) {
//            for (String originalHost : remoteRule.getOriginalHostList()) {
//                if (StringUtils.equals(originalHost, host)
//                        || StringUtils.endsWith(host, "." + originalHost)) {
//                    return remoteRule;
//                }
//            }
//        }
//
//        return null;
//    }

}
