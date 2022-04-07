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
import com.dataeye.proxy.apn.ApnProxyServer;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.HttpProxyHandler 14-1-8 16:13 (xmx) Exp $
 */
public class HttpProxyHandler extends ChannelInboundHandlerAdapter {

//    private static final Logger logger = LogbackRollingFileUtil.getLogger("HttpProxyHandler");
    private static final Logger logger = LoggerFactory.getLogger(HttpProxyHandler.class);


    public static final String HANDLER_NAME = "apnproxy.proxy";

    private Channel uaChannel;

    private String remoteAddr;

    private RemoteChannelInactiveCallback remoteChannelInactiveCallback;

    public HttpProxyHandler(Channel uaChannel, String remoteAddr,
                            RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.uaChannel = uaChannel;
        this.remoteAddr = remoteAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Remote channel: " + remoteAddr + " active");
        }
        ctx.read();
    }

    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        HttpObject ho = (HttpObject) msg;
        if (logger.isDebugEnabled()) {
            logger.debug("Recive From: " + remoteAddr + ", " + ho.getClass().getName());
        }

        if (ho instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) ho;
            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.KEEP_ALIVE);
        }

        if (ho instanceof HttpContent) {
            ((HttpContent) ho).retain();
        }


        if (uaChannel.isActive()) {
            uaChannel.writeAndFlush(ho).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.read();
                        ctx.fireChannelRead(msg);
                    } else {
                        ReferenceCountUtil.release(msg);
                        ctx.close();
                    }
                }
            });
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Remote channel: " + remoteAddr + " inactive");
        }

        uaChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                remoteChannelInactiveCallback.remoteChannelInactiveCallback(ctx, remoteAddr);
            }
        });
        ctx.fireChannelInactive();

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

    public interface RemoteChannelInactiveCallback {
        public void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                                  String remoeAddr) throws Exception;
    }

}
