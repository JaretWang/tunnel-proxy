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


import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxySchemaHandler extends ChannelInboundHandlerAdapter {

//    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxySchemaHandler");
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    public static final String HANDLER_NAME = "apnproxy.schema";
    private final ApnHandlerParams apnHandlerParams;
    private final RequestMonitor requestMonitor = new RequestMonitor();
    private boolean needAllocate = true;

    public ApnProxySchemaHandler(ApnHandlerParams apnHandlerParams) {
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("schema channelActive");

        if (needAllocate) {
            logger.debug("needAllocate is true");
            // 分配ip
            ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
            TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
            RequestDistributeService requestDistributeService = apnHandlerParams.getRequestDistributeService();
            ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
            if (Objects.isNull(apnProxyRemote)) {
                requestDistributeService.handleProxyIpIsEmpty(ctx);
            }
            logger.info("schema 分配ip结果：{}", apnProxyRemote);
            ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(apnProxyRemote);

            apnHandlerParams.setRequestMonitor(requestMonitor);
            requestMonitor.setTunnelName(tunnelInstance.getAlias());
            requestMonitor.setBegin(System.currentTimeMillis());
            requestMonitor.setProxyAddr(apnProxyRemote.getIpAddr());
            requestMonitor.setExpireTime(apnProxyRemote.getExpireTime());
            requestMonitor.setSuccess(true);
            IpMonitorUtils.invoke(true, requestMonitor, true, HANDLER_NAME);

            needAllocate = false;
        } else {
            logger.debug("needAllocate is false");
        }


        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("schema channelInactive");

        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
        logger.info("schema channelRead");

        int bandwidth = 0;
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
            }
            else {
                //TODO 临时增加
                ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);
            }
            bandwidth += httpRequest.toString().getBytes().length;
            requestMonitor.setRequestType(httpRequest.method().name());
            requestMonitor.setTargetAddr(httpRequest.uri());
        }

        // 计算请求大小
        if (msg instanceof LastHttpContent) {
            requestMonitor.setBandwidth(bandwidth);
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("schema channelReadComplete");

        super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("schema exceptionCaught: {}",cause.getMessage());

        String message = cause.getCause().getMessage();
        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(message);
        ReqMonitorUtils.cost(requestMonitor, HANDLER_NAME);

        super.exceptionCaught(ctx, cause);
    }
}
