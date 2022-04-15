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
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.sun.org.apache.bcel.internal.generic.NEW;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxySchemaHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.schema";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxySchemaHandler");
    private final ApnHandlerParams apnHandlerParams;
    private final AtomicBoolean isAllocateIp = new AtomicBoolean(false);
    private long begin;

    public ApnProxySchemaHandler(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        begin = System.currentTimeMillis();

        apnHandlerParams.getRequestMonitor().setBegin(System.currentTimeMillis());

        // 分配ip
        if (!isAllocateIp.get()) {
            ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
            TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
            RequestDistributeService requestDistributeService = apnHandlerParams.getRequestDistributeService();
            ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
            if (Objects.isNull(apnProxyRemote)) {
                requestDistributeService.handleProxyIpIsEmpty(ctx);
            }
            ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(apnProxyRemote);
            isAllocateIp.compareAndSet(false, true);
        } else {
            ApnProxyRemote result = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            logger.info("Schema -> 已分配ip，再次提取，结果：{}", JSON.toJSONString(result));
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//        long cost = System.currentTimeMillis() - begin;
//        ApnProxyRemote apnProxyRemote = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
//        String ip = apnProxyRemote.getRemoteHost()+":"+apnProxyRemote.getRemotePort();
//        logger.info("ApnProxySchemaHandler 断开连接, 请求耗时 {} ms", cost);
//        ctx.read();

//        RequestMonitor requestMonitor = ctx.channel().attr(Global.REQUST_MONITOR_ATTRIBUTE_KEY).get();

//        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
//        requestMonitor.setCost(System.currentTimeMillis() - requestMonitor.getBegin());
//        logger.info("{} ms, {}, {}, {}, {}, {}, {}",
//                requestMonitor.getCost(),
//                requestMonitor.isSuccess(),
//                requestMonitor.getTunnelName(),
//                requestMonitor.getProxyAddr(),
//                requestMonitor.getRequestType(),
//                requestMonitor.getTargetAddr(),
//                requestMonitor.getFailReason());
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String message = cause.getCause().getMessage();
        apnHandlerParams.getRequestMonitor().setFailReason(message);

        super.exceptionCaught(ctx, cause);
    }
}
