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
import com.dataeye.proxy.apn.cons.ApnProxyConstants;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.apn.utils.Base64;
import com.dataeye.proxy.apn.utils.HostNamePortUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.ApnProxyForwardHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyForwardHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("ApnProxyForwardHandler");

    public static final String HANDLER_NAME = "apnproxy.forward";
    private String remoteAddr;
    private final Map<String, Channel> remoteChannelMap = new HashMap<>();
    private final Map<String, ApnProxyRemote> ipCache = new HashMap<>();
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final ApnProxyRemoteChooser apnProxyRemoteChooser;
    private final TunnelInstance tunnelInstance;
    private final RequestDistributeService requestDistributeService;
//    private ApnProxyRemote apnProxyRemote;
//    private final Bootstrap bootstrap = new Bootstrap();


    public ApnProxyForwardHandler(ApnHandlerParams apnHandlerParams) {
        this.apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
        this.tunnelInstance = apnHandlerParams.getTunnelInstance();
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
    }

//    @Override
//    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        this.apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
//        this.remoteAddr = apnProxyRemote.getRemote();
//        logger.info("ApnProxyTunnelHandler 连接成功, 分配IP: {}", apnProxyRemote.getRemote());
//    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
        final Channel uaChannel = ctx.channel();

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            logger.info("ApnProxyForwardHandler 接收请求, 类型 [{}]", httpRequest.method().name());

            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtil.getPort(originalHostHeader, 80);

//            final ApnProxyRemote apnProxyRemote = ApnProxyRemoteChooser.chooseRemoteAddr(
//                    originalHost, originalPort);

            ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
            remoteAddr = apnProxyRemote.getRemote();
            Channel remoteChannel = remoteChannelMap.get(remoteAddr);


//            Object obj = ctx.channel().attr(AttributeKey.valueOf(remoteAddr)).get();
//            ApnProxyRemote apnProxyRemote;
//            if (Objects.nonNull(obj)) {
//                logger.warn("ip 缓存检查，存在ip");
//                apnProxyRemote = (ApnProxyRemote) obj;
//            } else {
//                logger.error("ip 缓存检查，不存在，重新赋值");
//                ApnProxyRemote proxyConfig = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
//                ctx.channel().attr(AttributeKey.valueOf(remoteAddr)).set(proxyConfig);
//                apnProxyRemote = proxyConfig;
//            }

            logger.info("缓存通道检查");
            if (remoteChannel != null && remoteChannel.isActive()) {
                logger.info("使用已创建的通道 to {} for {}:{}", remoteAddr, originalHost, originalPort);
//                HttpRequest request = constructRequestForProxy(httpRequest, apnProxyRemote);
                HttpRequest request = requestDistributeService.constructRequestForProxyByForward(httpRequest, apnProxyRemote);
                remoteChannel.attr(ApnProxyConstants.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
                remoteChannel.writeAndFlush(request).addListener((ChannelFutureListener) future -> future.channel().read());
            } else {
                logger.info("ApnProxyForwardHandler 创建新的通道 to {} for {}:{}", remoteAddr, originalHost, originalPort);
                requestDistributeService.sendRequestByForward(uaChannel, remoteChannel, httpRequest, apnProxyRemote,
                        tunnelInstance, httpContentBuffer, remoteChannelMap, msg);

//                RemoteChannelInactiveCallback cb = new RemoteChannelInactiveCallback() {
//                    @Override
//                    public void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
//                                                              String inactiveRemoteAddr)
//                            throws Exception {
//                        if (logger.isDebugEnabled()) {
//                            logger.debug("Remote channel: " + inactiveRemoteAddr
//                                    + " inactive, and flush end");
//                        }
//                        uaChannel.close();
//                        remoteChannelMap.remove(inactiveRemoteAddr);
//                    }
//
//                };
//
////                if (logger.isDebugEnabled()) {
////                    logger.debug("Create new remote channel to: " + remoteAddr + " for: "
////                            + originalHost + ":" + originalPort);
////                }
//
//                logger.info("创建新的通道 to {} for {}:{}", remoteAddr, originalHost, originalPort);
//                logger.info("client 发起代理请求");
//                bootstrap
//                        .group(uaChannel.eventLoop())
//                        .channel(NioSocketChannel.class)
////                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
//                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
//                        .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
//                        .option(ChannelOption.AUTO_READ, false)
//                        .handler(new HttpProxyChannelInitializer(apnProxyRemote, uaChannel, remoteAddr, cb));
//
//                // set local address
//                String remoteHost = apnProxyRemote.getRemoteHost();
//                String ip = ApnProxyLocalAddressChooser.choose(remoteHost);
//                if (StringUtils.isNotBlank(ip)) {
//                    logger.info("本地地址: {}", ip);
//                    bootstrap.localAddress(new InetSocketAddress(ip, 0));
//                }
//                int remotePort = apnProxyRemote.getRemotePort();
//                logger.info("代理ip: {}:{}", remoteHost, remotePort);
//
//                ChannelFuture remoteConnectFuture = bootstrap.connect(remoteHost, remotePort);
//
////                remoteChannel = remoteConnectFuture.channel();
////                remoteChannel.attr(ApnProxyConstants.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
////                remoteChannelMap.put(remoteAddr, remoteChannel);
//
//                remoteConnectFuture.addListener((ChannelFutureListener) future -> {
//                    if (future.isSuccess()) {
//                        logger.info("tunnel_handler 连接成功");
//
//                        HttpRequest newRequest = constructRequestForProxy((HttpRequest) msg, apnProxyRemote);
//                        future.channel().write(newRequest);
//
//                        logger.info("将缓存的 HttpContent 写回通道");
//                        for (HttpContent hc : httpContentBuffer) {
//                            future.channel().writeAndFlush(hc);
//                        }
//                        httpContentBuffer.clear();
//
//                        // EMPTY_BUFFER 会自动关闭
//                        future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
//                                .addListener((ChannelFutureListener) future1 -> future1.channel().read());
//                    } else {
//                        String errorMsg = "连接远程地址 [" + remoteAddr + "] 失败";
//                        logger.error(errorMsg);
//                        // send error response
//                        HttpMessage errorResponseMsg = HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
//                        uaChannel.writeAndFlush(errorResponseMsg);
//                        httpContentBuffer.clear();
//                        future.channel().close();
//                    }
//                });
            }
            ReferenceCountUtil.release(msg);
        } else {
            logger.info("缓存 HttpContent");
            Channel remoteChannel = remoteChannelMap.get(remoteAddr);

            HttpContent hc = ((HttpContent) msg);
            //hc.retain();
            //HttpContent _hc = hc.copy();

            if (remoteChannel != null && remoteChannel.isActive()) {
                remoteChannel.writeAndFlush(hc).addListener((ChannelFutureListener) future -> {
                    future.channel().read();
                    logger.debug("Remote channel: " + remoteAddr + " read after write http content");
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
            remoteChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener((ChannelFutureListener) future -> remoteChannel.close());
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