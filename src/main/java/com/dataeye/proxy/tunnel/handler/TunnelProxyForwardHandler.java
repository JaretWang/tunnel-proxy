package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.HandlerCons;
import com.dataeye.proxy.cons.ProxyConstants;
import com.dataeye.proxy.tunnel.initializer.TunnelHttpProxyChannelInitializer;
import com.dataeye.proxy.utils.HostNamePortUtils;
import com.dataeye.proxy.utils.HttpErrorUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author jaret
 * @date 2022/3/25 19:11
 * @description
 */
@Slf4j
public class TunnelProxyForwardHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy_forward";
    private String remoteAddr;
    private Map<String, Channel> remoteChannelMap = new HashMap<>();
    private List<HttpContent> httpContentBuffer = new ArrayList<>();
    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;

    public TunnelProxyForwardHandler(ProxyServerConfig proxyServerConfig, ProxySslContextFactory proxySslContextFactory) {
        this.proxyServerConfig = proxyServerConfig;
        this.proxySslContextFactory = proxySslContextFactory;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {

        final Channel uaChannel = ctx.channel();

        log.debug("TunnelProxyForwardHandler -> msg: {}", msg.toString());
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;

            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtils.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtils.getPort(originalHostHeader, HandlerCons.DEFAULT_HTTP_PORT);

            // todo 有更改
            remoteAddr = proxyServerConfig.getRemote();

            Channel remoteChannel = remoteChannelMap.get(remoteAddr);

            // 复用远程连接
            if (remoteChannel != null && remoteChannel.isActive()) {
                log.debug("Use old remote channel to: " + remoteAddr + " for: " + originalHost + ":" + originalPort);
                HttpRequest request = constructRequestForProxy(httpRequest, proxyServerConfig);
                remoteChannel.attr(ProxyConstants.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
                remoteChannel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().read();
                    }
                });
            }
            // 发起代理转发请求
            else {
                TunnelHttpProxyHandler.RemoteChannelInactiveCallback cb = new TunnelHttpProxyHandler.RemoteChannelInactiveCallback() {
                    @Override
                    public void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                                              String inactiveRemoteAddr)
                            throws Exception {
                        log.debug("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
                        uaChannel.close();
                        remoteChannelMap.remove(inactiveRemoteAddr);
                    }

                };

                log.debug("Create new remote channel to: " + remoteAddr + " for: " + originalHost + ":" + originalPort);

                Bootstrap bootstrap = new Bootstrap()
                        .group(uaChannel.eventLoop())
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                        .option(ChannelOption.AUTO_READ, false)
                        .handler(new TunnelHttpProxyChannelInitializer(proxyServerConfig, uaChannel, remoteAddr,
                                proxySslContextFactory, cb));

                // set local address
                // todo 改动
                bootstrap.localAddress(new InetSocketAddress(proxyServerConfig.getHost(), proxyServerConfig.getPort()));


                ChannelFuture remoteConnectFuture = bootstrap.connect(proxyServerConfig.getRemoteHost(), proxyServerConfig.getRemotePort());

                remoteChannel = remoteConnectFuture.channel();
                remoteChannel.attr(HandlerCons.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
                remoteChannelMap.put(remoteAddr, remoteChannel);

                remoteConnectFuture.addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        HttpRequest proxyRequest = constructRequestForProxy((HttpRequest) msg, proxyServerConfig);
                        future.channel().write(proxyRequest);

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
                        log.error(errorMsg);
                        // send error response
                        HttpMessage errorResponseMsg = HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                        uaChannel.writeAndFlush(errorResponseMsg);
                        httpContentBuffer.clear();

                        future.channel().close();
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
                        log.debug("Remote channel: " + remoteAddr + " read after write http content");
                    }
                });
            } else {
                httpContentBuffer.add(hc);
            }
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("UA channel: " + " inactive");
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
        log.error(cause.getMessage(), cause);
        ctx.close();
    }

    private HttpRequest constructRequestForProxy(HttpRequest httpRequest,
                                                 ProxyServerConfig proxyServerConfig) {

        String uri = httpRequest.getUri();

        if (!proxyServerConfig.isAppleyRemoteRule()) {
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

        if (StringUtils.isNotBlank(proxyServerConfig.getProxyUserName())
                && StringUtils.isNotBlank(proxyServerConfig.getProxyPassword())) {
            //todo 优化
            String proxyAuthorization = proxyServerConfig.getProxyUserName() + ":" + proxyServerConfig.getProxyPassword();
            _httpRequest.headers().set("Proxy-Authorization",
                    "Basic " + Base64.encodeBase64String(proxyAuthorization.getBytes(StandardCharsets.UTF_8)));

        }

        return _httpRequest;
    }

    private String getPartialUrl(String fullUrl) {
        if (StringUtils.startsWith(fullUrl, HandlerCons.PROTOCOL_HTTP)) {
            int idx = StringUtils.indexOf(fullUrl, "/", 7);
            return idx == -1 ? "/" : StringUtils.substring(fullUrl, idx);
        }

        return fullUrl;
    }

}
