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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyForwardHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

        private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyForwardHandler");
//    private static final Logger logger = LogbackRollingFileUtil.getLogger(ApnProxyForwardHandler.class);

    public static final String HANDLER_NAME = "apnproxy.forward";
//    private final Map<String, Channel> remoteChannelMap = new HashMap<>();
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final ApnProxyRemoteChooser apnProxyRemoteChooser;
    private final TunnelInstance tunnelInstance;
    private final RequestDistributeService requestDistributeService;
    private final ThreadPoolTaskExecutor ioThreadPool;
//    private String remoteAddr;

    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
        this.tunnelInstance = apnHandlerParams.getTunnelInstance();
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.ioThreadPool = apnHandlerParams.getIoThreadPool();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {
        final Channel uaChannel = ctx.channel();

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyForwardHandler 接收请求, 请求内容: {}", httpRequest.toString());

//            ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance,httpRequest);
//            if (Objects.isNull(apnProxyRemote)) {
//                handleProxyIpIsEmpty(ctx);
//            }
//            remoteAddr = apnProxyRemote.getRemote();
//
//            Channel remoteChannel = remoteChannelMap.get(remoteAddr);
//
//            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
//            String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
//            int originalPort = HostNamePortUtil.getPort(originalHostHeader, 80);

//            logger.info("缓存通道检查");
//            if (remoteChannel != null && remoteChannel.isActive()) {
//                logger.info("使用已创建的通道 to {} for {}:{}", remoteAddr, originalHost, originalPort);
//                HttpRequest request = requestDistributeService.constructRequestForProxyByForward(httpRequest, apnProxyRemote);
//                remoteChannel.attr(ApnProxyConstants.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
//                remoteChannel.writeAndFlush(request).addListener((ChannelFutureListener) future -> future.channel().read());
//            } else {
//                logger.info("ApnProxyForwardHandler 创建新的通道 to {} for {}:{}", remoteAddr, originalHost, originalPort);
//                requestDistributeService.sendRequestByForward(ioThreadPool, uaChannel, remoteChannel, httpRequest, apnProxyRemote,
//                        tunnelInstance, httpContentBuffer, remoteChannelMap, msg);

            requestDistributeService.sendRequestByForward(ioThreadPool, uaChannel, httpRequest, tunnelInstance,
                    httpContentBuffer, ctx, msg, apnProxyRemoteChooser);

//            }
            ReferenceCountUtil.release(msg);
        } else {
            logger.info("缓存 HttpContent");
            HttpContent hc = ((HttpContent) msg);

//            Channel remoteChannel = remoteChannelMap.get(remoteAddr);
//
//            //hc.retain();
//            //HttpContent _hc = hc.copy();
//
//            if (remoteChannel != null && remoteChannel.isActive()) {
//                remoteChannel.writeAndFlush(hc).addListener((ChannelFutureListener) future -> {
//                    future.channel().read();
//                    logger.debug("Remote channel: " + remoteAddr + " read after write http content");
//                });
//            } else {
//            httpContentBuffer.add(hc);
//            }

            httpContentBuffer.add(hc);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("UA channel inactive");
        SocksServerUtils.closeOnFlush(ctx.channel());
//        for (Map.Entry<String, Channel> entry : remoteChannelMap.entrySet()) {
//            final Channel remoteChannel = entry.getValue();
//            remoteChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
//                    .addListener((ChannelFutureListener) future -> remoteChannel.close());
//        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        SocksServerUtils.closeOnFlush(ctx.channel());
//        ctx.close();
    }

//    public void handleProxyIpIsEmpty(ChannelHandlerContext ctx){
//        String errMsg = "获取代理IP为空，请30s后重试";
//        logger.error(errMsg);
//        ByteBuf content = Unpooled.copiedBuffer(errMsg, CharsetUtil.UTF_8);
//        FullHttpMessage errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
//        ctx.channel().writeAndFlush(errorResponse);
//        SocksServerUtils.closeOnFlush(ctx.channel());
//    }

}