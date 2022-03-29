package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.http.handler.SecondForwardHandler;
import com.dataeye.proxy.http.handler.ThreadForwardHandler;
import com.dataeye.proxy.tunnel.initializer.TunnelClientChannelInitializer;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.UnsupportedEncodingException;
import java.util.Set;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * @author jaret
 * @date 2022/3/25 18:14
 * @description
 */
@Deprecated
@Slf4j
public class TunnelProxyHandler1 extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy";

    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;

    public TunnelProxyHandler1(ProxyServerConfig proxyServerConfig,
                              ProxySslContextFactory proxySslContextFactory) {
        this.proxyServerConfig = proxyServerConfig;
        this.proxySslContextFactory = proxySslContextFactory;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            log.debug("TunnelProxyHandler 接收到请求内容: {}", httpRequest.toString());

            Channel uaChannel = ctx.channel();

            // connect remote
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(uaChannel.eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new TunnelClientChannelInitializer(proxyServerConfig, uaChannel, proxySslContextFactory));

            log.warn("代理商地址:{}, 端口：{}, 监听类型：{}", proxyServerConfig.getRemoteHost(), proxyServerConfig.getRemotePort(), proxyServerConfig.getRemoteListenType());
            String remoteHost = proxyServerConfig.getRemoteHost();
            int remotePort = proxyServerConfig.getRemotePort();
            bootstrap.connect(remoteHost,remotePort)
                    .addListener((ChannelFutureListener) future1 -> {
                        if (future1.isSuccess()) {
                            // successfully connect to the original server
                            // send connect success msg to UA

                            if (proxyServerConfig.isAppleyRemoteRule()) {
                                ctx.pipeline().remove("codec");
                                ctx.pipeline().remove(TunnelProxyPreHandler.HANDLER_NAME);
                                ctx.pipeline().remove(TunnelProxyHandler.HANDLER_NAME);

                                // add relay handler
                                ctx.pipeline().addLast(new TunnelProxyRelayHandler("UA --> Remote", future1.channel()));

                                String data = constructConnectRequestForProxy(httpRequest, proxyServerConfig);
                                ByteBuf content = Unpooled.copiedBuffer(data, CharsetUtil.UTF_8);
                                future1.channel()
                                        .writeAndFlush(content)
                                        .addListener((ChannelFutureListener) future2 -> {
                                            if (!future2.channel().config()
                                                    .getOption(ChannelOption.AUTO_READ)) {
                                                future2.channel().read();
                                            }
                                        });

                            } else {
                                HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(
                                        HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "Connection established"));
                                ctx.writeAndFlush(proxyConnectSuccessResponse).addListener(
                                        (ChannelFutureListener) future2 -> {
                                            // remove handlers
                                            ctx.pipeline().remove("codec");
                                            ctx.pipeline().remove(TunnelProxyPreHandler.HANDLER_NAME);
                                            ctx.pipeline().remove(TunnelProxyHandler.HANDLER_NAME);

                                            // add relay handler
                                            String tag = "UA --> " + proxyServerConfig.getRemote();
                                            ctx.pipeline().addLast(new TunnelProxyRelayHandler(tag, future1.channel()));
                                        });
                            }

                        } else {
                            if (ctx.channel().isActive()) {
                                ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                        .addListener(ChannelFutureListener.CLOSE);
                            }
                        }
                    });

        }
        ReferenceCountUtil.release(msg);
    }

    private String constructConnectRequestForProxy(HttpRequest httpRequest,
                                                   ProxyServerConfig proxyServerConfig) {
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

        if (StringUtils.isNotBlank(proxyServerConfig.getProxyUserName())
                && StringUtils.isNotBlank(proxyServerConfig.getProxyPassword())) {
            // todo 优化 credentials
            String proxyAuthorization = proxyServerConfig.getProxyUserName() + ":"
                    + proxyServerConfig.getProxyPassword();
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
        log.error(cause.getMessage(), cause);
        ctx.close();
    }
}
