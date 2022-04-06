package com.dataeye.proxy.tunnel.handler;

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.bean.ProxyType;
import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.bean.TunnelProxyListenType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.HandlerCons;
import com.dataeye.proxy.cons.ProxyConstants;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.service.ProxyService;
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
    public String remoteAddr;
    private final TunnelInstance tunnelInstance;
    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;
    private final ITunnelDistributeService tunnelDistributeService;
    private final ProxyService proxyService;
    private final Map<String, Channel> remoteChannelMap = new HashMap<>();
    private final List<HttpContent> httpContentBuffer = new ArrayList<>();

    public TunnelProxyForwardHandler(TunnelInstance tunnelInstance, ProxyServerConfig proxyServerConfig, ProxySslContextFactory proxySslContextFactory,
                                     ITunnelDistributeService tunnelDistributeService, ProxyService proxyService) {
        this.proxyService = proxyService;
        this.tunnelInstance = tunnelInstance;
        this.proxyServerConfig = proxyServerConfig;
        this.proxySslContextFactory = proxySslContextFactory;
        this.tunnelDistributeService = tunnelDistributeService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
        log.info("非 CONNECT 请求, 直接转发");

        final Channel uaChannel = ctx.channel();

        log.debug("TunnelProxyForwardHandler -> msg: {}", msg.toString());
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            log.debug("TunnelProxyForwardHandler 接收到请求内容: {}", httpRequest.toString());

            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtils.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtils.getPort(originalHostHeader, HandlerCons.DEFAULT_HTTP_PORT);

            // 隧道分配结果
            TunnelAllocateResult allocateResult = tunnelDistributeService.getDistributeParams(httpRequest, tunnelInstance);
            log.info("IP 分配结果：{}", allocateResult.toString());

//            TunnelAllocateResult allocateResult = TunnelAllocateResult.builder()
//                    .tunnelProxyListenType(TunnelProxyListenType.PLAIN).proxyType(ProxyType.exclusiveTunnel)
//                    .ip(HandlerCons.ip).port(HandlerCons.port).build();


//            ProxyCfg proxyCfg = proxyService.getOne().get();
//            log.warn("云代理");
//            TunnelAllocateResult allocateResult = TunnelAllocateResult.builder()
//                    .tunnelProxyListenType(TunnelProxyListenType.PLAIN).proxyType(ProxyType.exclusiveTunnel)
//                    .ip(proxyCfg.getHost()).port(proxyCfg.getPort())
//                    .username(proxyCfg.getUserName()).password(proxyCfg.getPassword()).build();

//            // todo 芝麻代理套餐有限，不利于测试，现在使用快代理测试，线上部署记得换回到上面的那个
//            log.warn("芝麻代理套餐有限，不利于测试，现在使用快代理测试，后续记得换回到上面的那个");
//            TunnelAllocateResult allocateResult = TunnelAllocateResult.builder()
//                    .tunnelProxyListenType(TunnelProxyListenType.PLAIN).proxyType(ProxyType.exclusiveTunnel)
//                    .ip(proxyServerConfig.getRemoteHost()).port(proxyServerConfig.getRemotePort())
//                    .username(proxyServerConfig.getProxyUserName()).password(proxyServerConfig.getProxyPassword()).build();


            log.info("通道复用检查");
            remoteAddr = allocateResult.getRemote();
            Channel remoteChannel = remoteChannelMap.get(remoteAddr);
            // 复用远程通道
            if (remoteChannel != null && remoteChannel.isActive()) {
                log.debug("Use old remote channel to: " + remoteAddr + " for: " + originalHost + ":" + originalPort);
                HttpRequest request = constructRequestForProxy(httpRequest, proxyServerConfig.isAppleyRemoteRule(), allocateResult);
                remoteChannel.attr(HandlerCons.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
                remoteChannel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().read();
                    }
                });
            }
            // 发起代理转发请求
            else {
                TunnelHttpProxyHandler.RemoteChannelInactiveCallback cb = (remoteChannelCtx, inactiveRemoteAddr) -> {
                    log.debug("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
                    uaChannel.close();
                    remoteChannelMap.remove(inactiveRemoteAddr);
                };

                log.info("创建新的远程通道 to: " + remoteAddr + " for: " + originalHost + ":" + originalPort);

                // 向代理商发送请求
                Bootstrap bootstrap = new Bootstrap()
                        .group(uaChannel.eventLoop())
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, proxyServerConfig.getBootstrapConnectTimeoutMillis())
                        .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                        .option(ChannelOption.AUTO_READ, false)
                        .handler(new TunnelHttpProxyChannelInitializer(allocateResult, uaChannel, proxySslContextFactory, cb));

                log.debug("开始连接代理ip");
                // todo 改动 放开的话 这里会报出重复绑定的错误
//                bootstrap.localAddress(new InetSocketAddress(proxyServerConfig.getHost(), proxyServerConfig.getPort()));
                ChannelFuture remoteConnectFuture = bootstrap.connect(allocateResult.getIp(), allocateResult.getPort());

                remoteChannel = remoteConnectFuture.channel();
                remoteChannel.attr(HandlerCons.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
                remoteChannelMap.put(remoteAddr, remoteChannel);

                remoteConnectFuture.addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.info("连接代理ip成功");
                        HttpRequest proxyRequest = constructRequestForProxy((HttpRequest) msg, proxyServerConfig.isAppleyRemoteRule(), allocateResult);
                        future.channel().write(proxyRequest);

                        for (HttpContent hc : httpContentBuffer) {
                            future.channel().writeAndFlush(hc);
                        }
                        httpContentBuffer.clear();
                        log.info("回放 httpContent");

                        future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                .addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture future) throws Exception {
                                        future.channel().read();
                                    }
                                });
                    } else {
                        log.error("连接代理ip失败");
                        String errorMsg = "remote connect to " + remoteAddr + " fail, 原因:" + future.cause().toString();
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
            log.info("缓存 httpContent, 消息类型：{}", msg.getClass());
            HttpContent hc = ((HttpContent) msg);
            //hc.retain();

            //HttpContent _hc = hc.copy();

            Channel remoteChannel = remoteChannelMap.get(remoteAddr);
            if (remoteChannel != null && remoteChannel.isActive()) {
                remoteChannel.writeAndFlush(hc).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        log.error("TunnelProxyForwardHandler 出现异常: {}", cause.getCause().getMessage());
        ctx.close();
    }

    private HttpRequest constructRequestForProxy(HttpRequest httpRequest, boolean appleyRemoteRule,
                                                 TunnelAllocateResult tunnelAllocateResult) {

        log.info("为代理ip构造请求");
        String uri = httpRequest.getUri();

        if (!appleyRemoteRule) {
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

        String username = tunnelAllocateResult.getUsername();
        String password = tunnelAllocateResult.getPassword();
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            //todo 优化
            String proxyAuthorization = username + ":" + password;
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
