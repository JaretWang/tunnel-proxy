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

import com.dataeye.proxy.apn.remotechooser.ApnProxyLocalAddressChooser;
import com.dataeye.proxy.apn.initializer.ApnProxyTunnelChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.utils.Base64;
import com.dataeye.proxy.apn.utils.HostNamePortUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyTunnelHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ApnProxyTunnelHandler.class);
    public static final String HANDLER_NAME = "apnproxy.tunnel";
    private ApnProxyRemoteChooser apnProxyRemoteChooser;
    private TunnelInstance tunnelInstance;

    public ApnProxyTunnelHandler(ApnProxyRemoteChooser apnProxyRemoteChooser, TunnelInstance tunnelInstance){
        this.apnProxyRemoteChooser = apnProxyRemoteChooser;
        this.tunnelInstance = tunnelInstance;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;

            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtil.getPort(originalHostHeader, 443);

//            final ApnProxyRemote apnProxyRemote = ApnProxyRemoteChooser.chooseRemoteAddr(
//                    originalHost, originalPort);
            ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);

            Channel uaChannel = ctx.channel();

            // connect remote
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(uaChannel.eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ApnProxyTunnelChannelInitializer(apnProxyRemote, uaChannel));

            // set local address
            if (StringUtils.isNotBlank(ApnProxyLocalAddressChooser.choose(apnProxyRemote
                    .getRemoteHost()))) {
                bootstrap.localAddress(new InetSocketAddress((ApnProxyLocalAddressChooser
                        .choose(apnProxyRemote.getRemoteHost())), 0));
            }

            bootstrap.connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort())
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(final ChannelFuture future1) throws Exception {
                            if (future1.isSuccess()) {
                                // successfully connect to the original server
                                // send connect success msg to UA

                                if (apnProxyRemote.isAppleyRemoteRule()) {
                                    ctx.pipeline().remove("codec");
                                    ctx.pipeline().remove(ApnProxyPreHandler.HANDLER_NAME);
                                    ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

                                    // add relay handler
                                    ctx.pipeline().addLast(
                                            new ApnProxyRelayHandler("UA --> Remote", future1.channel()));

                                    future1
                                            .channel()
                                            .writeAndFlush(
                                                    Unpooled.copiedBuffer(
                                                            constructConnectRequestForProxy(httpRequest,
                                                                    apnProxyRemote), CharsetUtil.UTF_8))
                                            .addListener(new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture future2)
                                                        throws Exception {
                                                    if (!future2.channel().config()
                                                            .getOption(ChannelOption.AUTO_READ)) {
                                                        future2.channel().read();
                                                    }
                                                }
                                            });

                                } else {
                                    HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1, new HttpResponseStatus(200,
                                            "Connection established"));
                                    ctx.writeAndFlush(proxyConnectSuccessResponse).addListener(
                                            new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture future2)
                                                        throws Exception {
                                                    // remove handlers
                                                    ctx.pipeline().remove("codec");
                                                    ctx.pipeline().remove(ApnProxyPreHandler.HANDLER_NAME);
                                                    ctx.pipeline().remove(
                                                            ApnProxyTunnelHandler.HANDLER_NAME);

                                                    // add relay handler
                                                    ctx.pipeline().addLast(
                                                            new ApnProxyRelayHandler("UA --> "
                                                                    + apnProxyRemote
                                                                    .getRemote(), future1
                                                                    .channel()));
                                                }

                                            });
                                }

                            } else {
                                if (ctx.channel().isActive()) {
                                    ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        }
                    });

        }
        ReferenceCountUtil.release(msg);
    }

    private String constructConnectRequestForProxy(HttpRequest httpRequest,
                                                   ApnProxyRemote apnProxyRemote) {
        String CRLF = "\r\n";
        String url = httpRequest.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append(httpRequest.getMethod().name()).append(" ").append(url).append(" ")
                .append(httpRequest.getProtocolVersion().text()).append(CRLF);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
                continue;
            }

            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
                continue;
            }

            for (String headerValue : httpRequest.headers().getAll(headerName)) {
                sb.append(headerName).append(": ").append(headerValue).append(CRLF);
            }
        }

        if (StringUtils.isNotBlank(apnProxyRemote.getProxyUserName())
                && StringUtils.isNotBlank(apnProxyRemote.getProxyPassword())) {
            String proxyAuthorization = apnProxyRemote.getProxyUserName() + ":"
                    + apnProxyRemote.getProxyPassword();
            try {
                sb.append(
                        "Proxy-Authorization: Basic "
                                + Base64.encodeBase64String(proxyAuthorization.getBytes("UTF-8")))
                        .append(CRLF);
            } catch (UnsupportedEncodingException e) {
            }

        }

        sb.append(CRLF);

        return sb.toString();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }
}
