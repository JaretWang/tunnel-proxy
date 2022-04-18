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
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jaret
 * @date 2022/4/14 10:41
 */
public class ApnProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.tunnel";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyTunnelHandler");
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
        logger.info("Tunnel 建立连接");
//        if (!isAllocateIp.get()) {
//            ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
//            if (Objects.nonNull(cacheIpResult)) {
//                logger.info("Tunnel channelActive 检测到缓存的ip: {}", JSON.toJSONString(cacheIpResult));
//                return;
//            }
//
//            logger.info("Tunnel 未分配ip，开始分配");
//            ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
//            TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
//            apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
//            if (Objects.isNull(apnProxyRemote)) {
//                requestDistributeService.handleProxyIpIsEmpty(ctx);
//            }
//            String ipJson = JSON.toJSONString(apnProxyRemote);
//            logger.info("Tunnel IP 分配结果(建立连接时)：{}", ipJson);
//            ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(apnProxyRemote);
//            isAllocateIp.set(true);
//        } else {
//            ApnProxyRemote result = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
//            logger.info("Tunnel 已分配ip，再次提取，结果：{}", JSON.toJSONString(result));
//        }

    }


    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        logger.info("tunnel 读取数据");

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyTunnelHandler 接收请求, 请求内容: {}", httpRequest.toString());
            ApnProxyRemote cacheIpResult = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            if (Objects.nonNull(cacheIpResult)) {
                logger.info("tunnel 检测到缓存ip: {}", JSON.toJSONString(cacheIpResult));

                requestDistributeService.sendRequestByTunnel(cacheIpResult, apnHandlerParams, ctx, httpRequest);
            } else {
                ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
                TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
                ApnProxyRemote proxyConfig = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
                ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(proxyConfig);
                logger.info("tunnel 获取缓存ip为空，重新分配：{}", JSON.toJSONString(proxyConfig));

                requestDistributeService.sendRequestByTunnel(proxyConfig, apnHandlerParams, ctx, httpRequest);
            }
        }
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        requestMonitor.setSuccess(false);
        ReqMonitorUtils.cost(requestMonitor);
        super.channelInactive(ctx);
//        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        requestMonitor.setSuccess(false);
        ReqMonitorUtils.cost(requestMonitor);
        ctx.close();
    }

}
