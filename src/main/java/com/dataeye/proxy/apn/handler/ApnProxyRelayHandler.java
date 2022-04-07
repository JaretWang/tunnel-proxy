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

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyRelayHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyRelayHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ApnProxyRelayHandler.class);

    public static final String HANDLER_NAME = "apnproxy.relay";

    private final Channel relayChannel;
    private final String tag;

    public ApnProxyRelayHandler(String tag, Channel relayChannel) {
        this.tag = tag;
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(tag + " channel active");
        }

        if (!ctx.channel().config().getOption(ChannelOption.AUTO_READ)) {
            ctx.read();
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(tag + " : " + msg);
        }

        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!ctx.channel().config().getOption(ChannelOption.AUTO_READ)) {
                        ctx.read();
                    }
                }
            });
        } else {
            ReferenceCountUtil.release(msg);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(tag + " channel inactive");
        }
        if (relayChannel != null && relayChannel.isActive()) {
            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
                    ChannelFutureListener.CLOSE);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(tag, cause);
        ctx.close();
    }

}