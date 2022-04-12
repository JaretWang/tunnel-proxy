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


import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Objects;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyTunnelHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyTunnelHandler");
//    private static final Logger logger = LogbackRollingFileUtil.getLogger(ApnProxyTunnelHandler.class);

    public static final String HANDLER_NAME = "apnproxy.tunnel";
    private final ApnProxyRemoteChooser apnProxyRemoteChooser;
    private final TunnelInstance tunnelInstance;
    private final RequestDistributeService requestDistributeService;
    private final ThreadPoolTaskExecutor ioThreadPool;

    public ApnProxyTunnelHandler(ApnHandlerParams apnHandlerParams) {
        this.apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
        this.tunnelInstance = apnHandlerParams.getTunnelInstance();
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.ioThreadPool = apnHandlerParams.getIoThreadPool();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyTunnelHandler 接收请求, 请求内容: {}", httpRequest.toString());
            requestDistributeService.sendRequestByTunnel(ioThreadPool, ctx, httpRequest, tunnelInstance, apnProxyRemoteChooser);
        }
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

    public void handleProxyIpIsEmpty(ChannelHandlerContext ctx){
        String errMsg = "获取代理IP为空，请30s后重试";
        logger.error(errMsg);
        ByteBuf content = Unpooled.copiedBuffer(errMsg, CharsetUtil.UTF_8);
        FullHttpMessage errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
        ctx.channel().writeAndFlush(errorResponse);
        SocksServerUtils.closeOnFlush(ctx.channel());
    }



}
