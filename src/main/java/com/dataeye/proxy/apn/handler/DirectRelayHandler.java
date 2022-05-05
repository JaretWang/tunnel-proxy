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
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.HttpProxyHandler 14-1-8 16:13 (xmx) Exp $
 */
public class DirectRelayHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.proxy";
    //    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("DirectRelayHandler");
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final Channel uaChannel;
    private final String remoteAddr;
    private final RemoteChannelInactiveCallback remoteChannelInactiveCallback;
    private final RequestMonitor requestMonitor;
    private boolean first = true;

    public DirectRelayHandler(ApnHandlerParams apnHandlerParams, Channel uaChannel, String remoteAddr,
                              RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.requestMonitor = apnHandlerParams.getRequestMonitor();
        this.uaChannel = uaChannel;
        this.remoteAddr = remoteAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("DirectRelayHandler channelActive: Remote channel: {} active", remoteAddr);
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        logger.info("DirectRelayHandler channelRead");

//        HttpObject ho = (HttpObject) msg;
//        logger.info("Recive From: " + remoteAddr + ", " + ho.getClass().getName());
//
//        if (ho instanceof HttpResponse) {
//            HttpResponse httpResponse = (HttpResponse) ho;
//            logger.info("HttpProxyHandler -> HttpResponse:{}", httpResponse.toString());
//            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.KEEP_ALIVE);
//
//            //todo 使用短连接
////            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
////            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.CLOSE);
//        }
//
//        if (ho instanceof HttpContent) {
//            HttpContent httpContent = (HttpContent) ho;
//            logger.info("HttpProxyHandler -> HttpContent retain:{}", httpContent.toString());
//            httpContent.retain();
////            ((HttpContent) ho).retain();
//        }
//        if (uaChannel.isActive()) {
//            uaChannel.writeAndFlush(ho)
//                    .addListener((ChannelFutureListener) future -> {
//                        if (future.isSuccess()) {
//                            ctx.read();
//                            ctx.fireChannelRead(msg);
//
////                            // todo 临时补充
////                            ctx.close();
//                        } else {
//                            ReferenceCountUtil.release(msg);
//                            ctx.close();
//                        }
//
//                        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
//                        requestMonitor.setCost(System.currentTimeMillis() - requestMonitor.getBegin());
//                        logger.info("{} ms, {}, {}, {}, {}, {}, {}",
//                                requestMonitor.getCost(),
//                                requestMonitor.isSuccess(),
//                                requestMonitor.getTunnelName(),
//                                requestMonitor.getProxyAddr(),
//                                requestMonitor.getRequestType(),
//                                requestMonitor.getTargetAddr(),
//                                requestMonitor.getFailReason());
//                    });
//        }


        if (msg instanceof FullHttpResponse) {
            FullHttpResponse httpResponse = (FullHttpResponse) msg;
//            logger.info("HttpProxyHandler -> httpResponse:{}", httpResponse.toString());
//            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.KEEP_ALIVE);

            //todo 使用短连接
            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.CLOSE);

            httpResponse.retain();

            if (uaChannel.isActive()) {
                uaChannel.writeAndFlush(httpResponse)
                        .addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                ctx.read();
                                ctx.fireChannelRead(msg);

                                // todo 依然会执行两次，应该放入 attribute key
                                if (first) {
                                    // todo 临时增加
                                    requestMonitor.setSuccess(true);
                                    ReqMonitorUtils.cost(requestMonitor, "DirectRelayHandler isSuccess");
                                    IpMonitorUtils.invoke(requestMonitor, true, "DirectRelayHandler isSuccess");
                                    first = false;
                                }
                            } else {
                                ReferenceCountUtil.release(msg);
                                ctx.close();

                                if (first) {
                                    // todo 临时增加
                                    requestMonitor.setSuccess(false);
                                    requestMonitor.setFailReason(future.cause().getMessage());
                                    ReqMonitorUtils.cost(requestMonitor, "DirectRelayHandler isError");
                                    IpMonitorUtils.invoke(requestMonitor, false, "DirectRelayHandler isError");
                                    first = false;
                                }
                            }
                        });
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("DirectRelayHandler channelReadComplete");

        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        logger.info("DirectRelayHandler channelInactive, Remote channel: {} inactive", remoteAddr);

        uaChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener((ChannelFutureListener) future ->
                        remoteChannelInactiveCallback.remoteChannelInactiveCallback(ctx, remoteAddr));
        ctx.fireChannelInactive();

//        //todo 增加
//        ctx.channel().close();
//        uaChannel.close();
        ctx.close();

//        requestMonitor.setSuccess(true);
//        ReqMonitorUtils.cost(requestMonitor, "DirectRelayHandler channelInactive");
//        IpMonitorUtils.invoke(requestMonitor, true, "DirectRelayHandler channelInactive");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("DirectRelayHandler exceptionCaught: {}",cause.getMessage());

        //todo 增加
//        ctx.channel().close();
        ctx.close();

        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(cause.getMessage());
        ReqMonitorUtils.cost(requestMonitor, "DirectRelayHandler exceptionCaught");
        IpMonitorUtils.invoke(requestMonitor, false, "DirectRelayHandler exceptionCaught");
    }

    public interface RemoteChannelInactiveCallback {
        void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                           String remoeAddr) throws Exception;
    }

}
