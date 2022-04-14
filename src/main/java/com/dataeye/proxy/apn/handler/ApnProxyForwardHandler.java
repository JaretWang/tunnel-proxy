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
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.forward";
    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyForwardHandler");
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;
    private final AtomicBoolean isAllocateIp = new AtomicBoolean(false);
    private ApnProxyRemote apnProxyRemote = null;

    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("forward 建立连接");
//        if (!isAllocateIp.get()) {
//            ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
//            if (Objects.nonNull(cacheIpResult)) {
//                logger.info("forward 检测到缓存的ip: {}",JSON.toJSONString(cacheIpResult));
//                return;
//            }
//
//            logger.info("Forward 未分配ip，开始分配");
//            ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
//            TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
//            this.apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
//            if (Objects.isNull(this.apnProxyRemote)) {
//                requestDistributeService.handleProxyIpIsEmpty(ctx);
//            }
//            logger.info("Forward IP 分配结果(建立连接时)：{}", JSON.toJSONString(this.apnProxyRemote));
//
//            ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(this.apnProxyRemote);
//            isAllocateIp.compareAndSet(false, true);
//        } else {
//            ApnProxyRemote result = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
//            logger.info("Forward 已分配ip，再次提取，结果：{}", JSON.toJSONString(result));
//        }

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {
        logger.info("forward 读取数据");

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyForwardHandler 接收请求, 请求内容: {}", httpRequest.toString());
            ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            if (Objects.nonNull(cacheIpResult)) {
                logger.info("forward 检测到缓存的ip: {}", JSON.toJSONString(cacheIpResult));

                requestDistributeService.sendRequestByForward(cacheIpResult, apnHandlerParams, httpRequest, httpContentBuffer, ctx, msg);
            } else {
                ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
                TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
                ApnProxyRemote proxyConfig = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
                ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(proxyConfig);
                logger.info("forward 获取缓存ip为空，重新分配：{}", JSON.toJSONString(proxyConfig));

                requestDistributeService.sendRequestByForward(proxyConfig, apnHandlerParams, httpRequest, httpContentBuffer, ctx, msg);
            }
            ReferenceCountUtil.release(msg);

        } else {
            HttpContent hc = ((HttpContent) msg);
            httpContentBuffer.add(hc);
            logger.info("缓存 HttpContent, msg 类型：{}", msg.getClass());
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("forward 关闭连接");


        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

}