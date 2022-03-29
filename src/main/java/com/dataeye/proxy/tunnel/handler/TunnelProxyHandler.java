package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.TunnelManageConfig;
import com.dataeye.proxy.http.handler.SecondForwardHandler;
import com.dataeye.proxy.service.TunnelDistributeService;
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
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.protocol.HTTP;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.UnsupportedEncodingException;
import java.util.Set;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

/**
 * @author jaret
 * @date 2022/3/25 18:14
 * @description
 */
@Slf4j
public class TunnelProxyHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy";

    private final ProxyServerConfig proxyServerConfig;
    private final ProxySslContextFactory proxySslContextFactory;
    private final ThreadPoolTaskExecutor ioThreadPool;
    private final TunnelDistributeService tunnelDistributeService;
    private final TunnelManageConfig tunnelManageConfig;
//    private HttpRequest cacheRequest;

    public TunnelProxyHandler(ProxyServerConfig proxyServerConfig,
                              ProxySslContextFactory proxySslContextFactory,
                              ThreadPoolTaskExecutor ioThreadPool,
                              TunnelDistributeService tunnelDistributeService,
                              TunnelManageConfig tunnelManageConfig) {
        this.proxyServerConfig = proxyServerConfig;
        this.proxySslContextFactory = proxySslContextFactory;
        this.ioThreadPool = ioThreadPool;
        this.tunnelDistributeService = tunnelDistributeService;
        this.tunnelManageConfig = tunnelManageConfig;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            log.debug("TunnelProxyHandler 接收到请求内容: {}", httpRequest.toString());
            // 分发隧道
            TunnelAllocateResult allocateResult = tunnelDistributeService.distribute(httpRequest);
            // 提交代理请求任务
            ProxyRequestTask proxyRequestTask = new ProxyRequestTask(ctx, httpRequest, allocateResult);
            ioThreadPool.submit(proxyRequestTask);
            log.info("提交一个任务");
        }
        ReferenceCountUtil.release(msg);


//        if (msg instanceof HttpRequest) {
//            log.warn("读取请求头, 消息类型：{}", msg.getClass());
//            final HttpRequest httpRequest = (HttpRequest) msg;
//            cacheRequest = (HttpRequest) msg;
//            log.info("TunnelProxyHandler 接收到请求内容: {}", httpRequest.toString());
//        } else if (msg instanceof HttpContent) {
//            log.warn("读取body...");
//            // 提交代理请求任务
//            ProxyRequestTask proxyRequestTask = new ProxyRequestTask(ctx, cacheRequest);
//            ioThreadPool.submit(proxyRequestTask);
//        } else {
//            ReferenceCountUtil.release(msg);
//        }
    }


    /**
     * 代理请求任务
     */
    class ProxyRequestTask implements Runnable {

        private final ChannelHandlerContext ctx;
        private final HttpRequest httpRequest;
        private final TunnelAllocateResult allocateResult;

        public ProxyRequestTask(ChannelHandlerContext ctx, HttpRequest httpRequest, TunnelAllocateResult allocateResult) {
            this.ctx = ctx;
            this.httpRequest = httpRequest;
            this.allocateResult = allocateResult;
        }

        @Override
        public void run() {
            String remoteHost = allocateResult.getIp();
            int remotePort = allocateResult.getPort();
            log.warn("代理商地址:{}, 端口：{}, 监听类型：{}", remoteHost, remotePort, proxyServerConfig.getRemoteListenType());

            Channel uaChannel = ctx.channel();
            Bootstrap bootstrap = new Bootstrap()
                    .group(uaChannel.eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelManageConfig.getConnectTimeoutMillis())
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new TunnelClientChannelInitializer(proxyServerConfig, uaChannel, proxySslContextFactory));
            // 发起连接
            log.info("发起连接");
            bootstrap.connect(remoteHost, remotePort)
                    .addListener((ChannelFutureListener) future1 -> {
                        if (future1.isSuccess()) {
                            // successfully connect to the original server
                            // send connect success msg to UA
                            doAfterConnectSuccess(ctx, future1, httpRequest);
                        } else {
                            SocksServerUtils.closeOnFlush(ctx.channel());
                        }
                    });
        }
    }

    /**
     * 连接成功之后的操作
     *
     * @param ctx
     * @param future1
     * @param httpRequest
     */
    void doAfterConnectSuccess(ChannelHandlerContext ctx, ChannelFuture future1, HttpRequest httpRequest) {
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
                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
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
    }

    /**
     * 构造代理链接请求
     *
     * @param httpRequest
     * @param proxyServerConfig
     * @return
     */
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
