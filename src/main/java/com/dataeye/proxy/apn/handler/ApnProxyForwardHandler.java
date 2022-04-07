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

import com.dataeye.proxy.apn.cons.ApnProxyConstants;
import com.dataeye.proxy.apn.remotechooser.ApnProxyLocalAddressChooser;
import com.dataeye.proxy.apn.initializer.HttpProxyChannelInitializer;
import com.dataeye.proxy.apn.handler.HttpProxyHandler.RemoteChannelInactiveCallback;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.utils.Base64;
import com.dataeye.proxy.apn.utils.HostNamePortUtil;
import com.dataeye.proxy.apn.utils.HttpErrorUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyForwardHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ApnProxyForwardHandler.class);
    public static final String HANDLER_NAME = "apnproxy.forward";
    private String remoteAddr;
    private Map<String, Channel> remoteChannelMap = new HashMap<String, Channel>();
    private List<HttpContent> httpContentBuffer = new ArrayList<HttpContent>();
    private ApnProxyRemoteChooser apnProxyRemoteChooser;
    private TunnelInstance tunnelInstance;

    public ApnProxyForwardHandler(ApnProxyRemoteChooser apnProxyRemoteChooser, TunnelInstance tunnelInstance){
        this.apnProxyRemoteChooser = apnProxyRemoteChooser;
        this.tunnelInstance = tunnelInstance;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {

        final Channel uaChannel = ctx.channel();

        if (logger.isDebugEnabled()) {
            logger.debug(msg.toString());
        }
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;

            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtil.getPort(originalHostHeader, 80);

//            final ApnProxyRemote apnProxyRemote = ApnProxyRemoteChooser.chooseRemoteAddr(
//                    originalHost, originalPort);
            ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);

            remoteAddr = apnProxyRemote.getRemote();

            Channel remoteChannel = remoteChannelMap.get(remoteAddr);

            if (remoteChannel != null && remoteChannel.isActive()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Use old remote channel to: " + remoteAddr + " for: "
                            + originalHost + ":" + originalPort);
                }
                HttpRequest request = constructRequestForProxy(httpRequest, apnProxyRemote);
                remoteChannel.attr(ApnProxyConstants.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
                remoteChannel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().read();
                    }
                });
            } else {
                RemoteChannelInactiveCallback cb = new RemoteChannelInactiveCallback() {
                    @Override
                    public void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                                              String inactiveRemoteAddr)
                            throws Exception {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Remote channel: " + inactiveRemoteAddr
                                    + " inactive, and flush end");
                        }
                        uaChannel.close();
                        remoteChannelMap.remove(inactiveRemoteAddr);
                    }

                };

                if (logger.isDebugEnabled()) {
                    logger.debug("Create new remote channel to: " + remoteAddr + " for: "
                            + originalHost + ":" + originalPort);
                }

                Bootstrap bootstrap = new Bootstrap();
                bootstrap
                        .group(uaChannel.eventLoop())
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                        .option(ChannelOption.AUTO_READ, false)
                        .handler(
                                new HttpProxyChannelInitializer(apnProxyRemote, uaChannel, remoteAddr, cb));

                // set local address
                if (StringUtils.isNotBlank(ApnProxyLocalAddressChooser.choose(apnProxyRemote
                        .getRemoteHost()))) {
                    bootstrap.localAddress(new InetSocketAddress((ApnProxyLocalAddressChooser
                            .choose(apnProxyRemote.getRemoteHost())), 0));
                }

                ChannelFuture remoteConnectFuture = bootstrap.connect(
                        apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort());

                remoteChannel = remoteConnectFuture.channel();
                remoteChannel.attr(ApnProxyConstants.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
                remoteChannelMap.put(remoteAddr, remoteChannel);

                remoteConnectFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            future.channel().write(
                                    constructRequestForProxy((HttpRequest) msg, apnProxyRemote));

                            for (HttpContent hc : httpContentBuffer) {
                                future.channel().writeAndFlush(hc);
                            }
                            httpContentBuffer.clear();

                            future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                    .addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(ChannelFuture future)
                                                throws Exception {
                                            future.channel().read();
                                        }
                                    });
                        } else {
                            String errorMsg = "remote connect to " + remoteAddr + " fail";
                            logger.error(errorMsg);
                            // send error response
                            HttpMessage errorResponseMsg = HttpErrorUtil.buildHttpErrorMessage(
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                            uaChannel.writeAndFlush(errorResponseMsg);
                            httpContentBuffer.clear();

                            future.channel().close();
                        }
                    }
                });

            }
            ReferenceCountUtil.release(msg);
        } else {
            Channel remoteChannel = remoteChannelMap.get(remoteAddr);

            HttpContent hc = ((HttpContent) msg);
            //hc.retain();

            //HttpContent _hc = hc.copy();

            if (remoteChannel != null && remoteChannel.isActive()) {
                remoteChannel.writeAndFlush(hc).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().read();
                        if (logger.isDebugEnabled()) {
                            logger.debug("Remote channel: " + remoteAddr
                                    + " read after write http content");
                        }
                    }
                });
            } else {
                httpContentBuffer.add(hc);
            }
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("UA channel: " + " inactive");
        }
        for (Map.Entry<String, Channel> entry : remoteChannelMap.entrySet()) {
            final Channel remoteChannel = entry.getValue();
            remoteChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            remoteChannel.close();
                        }
                    });
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

    private HttpRequest constructRequestForProxy(HttpRequest httpRequest,
                                                 ApnProxyRemote apnProxyRemote) {

        String uri = httpRequest.getUri();

        if (!apnProxyRemote.isAppleyRemoteRule()) {
            uri = this.getPartialUrl(uri);
        }

        HttpRequest _httpRequest = new DefaultHttpRequest(httpRequest.getProtocolVersion(),
                httpRequest.getMethod(), uri);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
            // if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
            // continue;
            // }
            //
            // if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
            // continue;
            // }

            _httpRequest.headers().add(headerName, httpRequest.headers().getAll(headerName));
        }

        _httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        // _httpRequest.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.IDENTITY);

        if (StringUtils.isNotBlank(apnProxyRemote.getProxyUserName())
                && StringUtils.isNotBlank(apnProxyRemote.getProxyPassword())) {
            String proxyAuthorization = apnProxyRemote.getProxyUserName() + ":"
                    + apnProxyRemote.getProxyPassword();
            try {
                _httpRequest.headers().set("Proxy-Authorization",
                        "Basic " + Base64.encodeBase64String(proxyAuthorization.getBytes("UTF-8")));
            } catch (UnsupportedEncodingException e) {
            }

        }

        return _httpRequest;
    }

    private String getPartialUrl(String fullUrl) {
        if (StringUtils.startsWith(fullUrl, "http")) {
            int idx = StringUtils.indexOf(fullUrl, "/", 7);
            return idx == -1 ? "/" : StringUtils.substring(fullUrl, idx);
        }

        return fullUrl;
    }

}