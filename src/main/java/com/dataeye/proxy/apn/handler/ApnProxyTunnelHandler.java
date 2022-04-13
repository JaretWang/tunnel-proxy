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

package com.dataeye.proxy.apn.handler;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyPlainRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyTunnelHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.tunnel";
    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyTunnelHandler");
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;
    private final AtomicBoolean isAllocateIp = new AtomicBoolean(false);
    private ApnProxyRemote apnProxyRemote = null;

    public ApnProxyTunnelHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!isAllocateIp.get()) {
            logger.info("Tunnel 未分配ip，开始分配");
            ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
            TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
            apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
            String ipJson = JSON.toJSONString(apnProxyRemote);
            logger.info("Tunnel IP 分配结果(建立连接时)：{}", ipJson);
            if (Objects.isNull(apnProxyRemote)) {
                requestDistributeService.handleProxyIpIsEmpty(ctx);
            }
            ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(ipJson);
            isAllocateIp.set(true);
        } else {
            String ip = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            logger.info("Tunnel 已分配ip，再次提取，结果：{}", ip);
        }

    }


    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyTunnelHandler 接收请求, 请求内容: {}", httpRequest.toString());
            if (apnProxyRemote == null) {
                String ipJson = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
                JSONObject jsonObject = JSONObject.parseObject(ipJson);
                Boolean appleyRemoteRule = jsonObject.getBoolean("appleyRemoteRule");
                String proxyPassword = jsonObject.getString("proxyPassword");
                String proxyUserName = jsonObject.getString("proxyUserName");
                String remoteHost = jsonObject.getString("remoteHost");
                String remoteListenType = jsonObject.getString("remoteListenType");
                Integer remotePort = jsonObject.getInteger("remotePort");
                ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
                apPlainRemote.setAppleyRemoteRule(appleyRemoteRule);
                apPlainRemote.setRemoteListenType(ApnProxyListenType.valueOf(remoteListenType));
                apPlainRemote.setRemoteHost(remoteHost);
                apPlainRemote.setRemotePort(remotePort);
                apPlainRemote.setProxyUserName(proxyUserName);
                apPlainRemote.setProxyPassword(proxyPassword);

                logger.info("tunnel 分配ip结果为空，重新分配：{}", apPlainRemote.toString());
                requestDistributeService.sendRequestByTunnel(apPlainRemote, apnHandlerParams, ctx, httpRequest);
            } else {
                requestDistributeService.sendRequestByTunnel(apnProxyRemote, apnHandlerParams, ctx, httpRequest);
            }
        }
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

}
