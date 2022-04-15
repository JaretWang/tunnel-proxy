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
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.IpPoolScheduleService;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/4/14 10:38
 */
@Component
public class ApnProxyRemoteChooser {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyServer");

    private final AtomicInteger errorCount = new AtomicInteger(3);
    @Autowired IpPoolScheduleService ipPoolScheduleService;

    /**
     * 从代理ip池获取ip
     *
     * @param tunnelInstance
     * @return
     */
    public synchronized ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) {
        String proxyServer = tunnelInstance.getAlias();
        // todo 使用attribute改造
//        String method = httpRequest.method().name();
//        String protocolName = httpRequest.protocolVersion().protocolName();
//        String uri = httpRequest.uri();
//        String requestCombine = proxyServer + method + protocolName + uri;
//        String requestId = Md5Utils.md5Encode(requestCombine);
//        ApnProxyRemote apnProxyRemote = Global.REQUEST_IP_USE_RELATIONS.get(requestId);
//        if (Objects.nonNull(apnProxyRemote)) {
//            logger.info("存在该请求使用的ip缓存");
//            return apnProxyRemote;
//        }

        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
        ConcurrentLinkedQueue<ProxyCfg> proxyCfgsQueue = proxyIpPool.get(proxyServer);
        if (proxyCfgsQueue == null || proxyCfgsQueue.isEmpty()) {
            logger.error("实例 {} 对应的代理IP队列为空，需要重新加载", proxyServer);
            ipPoolScheduleService.initSingleServer(tunnelInstance);
            errorCount.decrementAndGet();
            if (errorCount.get() <= 0) {
                logger.error("连续 3 次初始化ip池失败, errorCount: {}", errorCount.get());
                errorCount.set(3);
                throw new RuntimeException("实例 " + proxyServer + " 对应的代理IP队列为空，连续 3 次重试失败");
//                return null;
            }
            return getProxyConfig(tunnelInstance);
        } else {
            errorCount.set(3);
            // TODO 这里可能有多线程安全问题,一个取,一个拿
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
                apPlainRemote.setExpireTime(poll.getExpireTime());

//                Global.REQUEST_IP_USE_RELATIONS.put(requestId, apPlainRemote);
                return apPlainRemote;
            }
            throw new RuntimeException("从队列中poll出来的ip为空");
        }
    }

}
