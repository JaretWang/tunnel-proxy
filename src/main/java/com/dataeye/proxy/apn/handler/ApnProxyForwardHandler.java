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
import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyForwardHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.forward";
    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyForwardHandler");
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;
    private ApnProxyRemote apnProxyRemote = null;
    private final AtomicBoolean isAllocateIp = new AtomicBoolean(false);

    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!isAllocateIp.get()) {
            logger.info("Forward 未分配ip，开始分配");
            ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
            TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
            apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
            String ipJson = JSON.toJSONString(apnProxyRemote);
            logger.info("Forward IP 分配结果(建立连接时)：{}", ipJson);
            if (Objects.isNull(apnProxyRemote)) {
                requestDistributeService.handleProxyIpIsEmpty(ctx);
            }
            ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(ipJson);
            isAllocateIp.set(true);
        } else {
            String ip = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            logger.info("Forward 已分配ip，再次提取，结果：{}", ip);
        }

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyForwardHandler 接收请求, 请求内容: {}", httpRequest.toString());
            requestDistributeService.sendRequestByForward(apnProxyRemote, apnHandlerParams, httpRequest, httpContentBuffer, ctx, msg);
            ReferenceCountUtil.release(msg);
        } else {
            HttpContent hc = ((HttpContent) msg);
            httpContentBuffer.add(hc);
            logger.info("缓存 HttpContent");
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("UA channel inactive");
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

}