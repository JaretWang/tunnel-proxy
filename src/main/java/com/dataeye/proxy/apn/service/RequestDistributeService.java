package com.dataeye.proxy.apn.service;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.handler.ApnProxyTunnelHandler;
import com.dataeye.proxy.apn.handler.ConcurrentLimitHandler;
import com.dataeye.proxy.apn.handler.DirectRelayHandler;
import com.dataeye.proxy.apn.handler.TunnelRelayHandler;
import com.dataeye.proxy.apn.initializer.DirectRelayChannelInitializer;
import com.dataeye.proxy.apn.initializer.TunnelRelayChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.utils.Base64;
import com.dataeye.proxy.apn.utils.HttpErrorUtil;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.IpPoolScheduleService;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/7 12:19
 * @description 请求分发
 */
@Service
public class RequestDistributeService {

    //    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("RequestDistributeService");
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    @Resource
    IpPoolScheduleService ipPoolScheduleService;

    /**
     * 转发 connect 请求
     *
     * @param apnHandlerParams
     * @param ctx
     * @param httpRequest
     */
    public void sendRequestByTunnel(ApnProxyRemote apnProxyRemote, ApnHandlerParams apnHandlerParams,
                                    final ChannelHandlerContext ctx,
                                    HttpRequest httpRequest) {
//        ApnProxyRemoteChooser apnProxyRemoteChooser = apnHandlerParams.getApnProxyRemoteChooser();
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
        ThreadPoolTaskExecutor ioThreadPool = apnHandlerParams.getIoThreadPool();

        // 隧道分配结果
//        ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
//        logger.info("转发 connect 请求 -> IP 分配结果：{}", JSON.toJSONString(apnProxyRemote));
//        if (Objects.isNull(apnProxyRemote)) {
//            handleProxyIpIsEmpty(ctx);
//        }

//        String remoteAddr = apnProxyRemote.getRemote();
//        String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
//        String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
//        int originalPort = HostNamePortUtil.getPort(originalHostHeader, 80);
//        String realAddr = originalHost + ":" + originalPort;
//        String realAddr = httpRequest.uri();、
        String remoteAddr = apnProxyRemote.getRemote();
        String realAddr = httpRequest.uri();
        logger.info("转发 CONNECT 请求 to {} for {}", remoteAddr, realAddr);
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
//        apnHandlerParams.getRequestMonitor().setTunnelName(tunnelInstance.getAlias());
//        String formatLocalDate = TimeUtils.formatLocalDate(apnProxyRemote.getExpireTime());
//        apnHandlerParams.getRequestMonitor().setProxyAddr(remoteAddr + "(" + formatLocalDate + ")");
//        apnHandlerParams.getRequestMonitor().setProxyAddr(remoteAddr);
//        apnHandlerParams.getRequestMonitor().setTargetAddr(realAddr);
//        apnHandlerParams.getRequestMonitor().setRequestType(httpRequest.method().name());

        // 提交代理请求任务
//        TunnelRequestTask proxyRequestTask = new TunnelRequestTask(requestMonitor, ctx, httpRequest, apnProxyRemote, tunnelInstance);
//        ioThreadPool.submit(proxyRequestTask);
        sendTunnelReq(requestMonitor, ctx, httpRequest, apnProxyRemote, tunnelInstance);
    }

    /**
     * 转发普通请求
     *
     * @param apnHandlerParams
     * @param httpRequest
     * @param httpContentBuffer
     * @param ctx
     * @param msg
     */
    public void sendRequestByForward(ApnProxyRemote apnProxyRemote, ApnHandlerParams apnHandlerParams,
                                     HttpRequest httpRequest,
                                     List<HttpContent> httpContentBuffer,
                                     ChannelHandlerContext ctx, Object msg) {
        final Channel uaChannel = ctx.channel();
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
        ThreadPoolTaskExecutor ioThreadPool = apnHandlerParams.getIoThreadPool();
//        String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
//        String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
//        int originalPort = HostNamePortUtil.getPort(originalHostHeader, 80);
//        String realAddr = originalHost + ":" + originalPort;
        String remoteAddr = apnProxyRemote.getRemote();
        String realAddr = httpRequest.uri();
        logger.info("转发普通请求 to {} for {}", remoteAddr, realAddr);
//        apnHandlerParams.getRequestMonitor().setTunnelName(tunnelInstance.getAlias());
//        String formatLocalDate = TimeUtils.formatLocalDate(apnProxyRemote.getExpireTime());
//        apnHandlerParams.getRequestMonitor().setProxyAddr(remoteAddr + "(" + formatLocalDate + ")");
//        apnHandlerParams.getRequestMonitor().setProxyAddr(remoteAddr);
//        apnHandlerParams.getRequestMonitor().setTargetAddr(realAddr);
//        apnHandlerParams.getRequestMonitor().setRequestType(httpRequest.method().name());

        // 提交代理请求任务
//        ForwardRequestTask proxyRequestTask = new ForwardRequestTask(uaChannel, apnHandlerParams, apnProxyRemote, tunnelInstance, httpContentBuffer, msg);
//        ioThreadPool.submit(proxyRequestTask);
        sendForwardReq(uaChannel, apnHandlerParams, apnProxyRemote, tunnelInstance, httpContentBuffer, msg);
    }

    public HttpRequest constructRequestForProxyByForward(HttpRequest httpRequest,
                                                         ApnProxyRemote apnProxyRemote) {

        String uri = httpRequest.getUri();

        if (!apnProxyRemote.isAppleyRemoteRule()) {
            uri = this.getPartialUrl(uri);
        }

        HttpRequest _httpRequest = new DefaultHttpRequest(httpRequest.getProtocolVersion(),
                httpRequest.getMethod(), uri);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
            // todo 放开请求，更改一下请求头
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Authorization")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
                continue;
            }

            _httpRequest.headers().add(headerName, httpRequest.headers().getAll(headerName));
        }

        // todo 更改长连接为短链接
        _httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
//        _httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//         _httpRequest.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.IDENTITY);

        if (StringUtils.isNotBlank(apnProxyRemote.getProxyUserName())
                && StringUtils.isNotBlank(apnProxyRemote.getProxyPassword())) {
            String proxyAuthorization = apnProxyRemote.getProxyUserName() + ":" + apnProxyRemote.getProxyPassword();
            try {
                _httpRequest.headers().set("Proxy-Authorization",
                        "Basic " + Base64.encodeBase64String(proxyAuthorization.getBytes("UTF-8")));
            } catch (UnsupportedEncodingException e) {
                System.out.println(e.getCause().getMessage());
            }

        }

        return _httpRequest;
    }

    public String getPartialUrl(String fullUrl) {
        if (StringUtils.startsWith(fullUrl, "http")) {
            int idx = StringUtils.indexOf(fullUrl, "/", 7);
            return idx == -1 ? "/" : StringUtils.substring(fullUrl, idx);
        }

        return fullUrl;
    }

    public String constructConnectRequestForProxyByTunnel(HttpRequest httpRequest,
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

            //todo 临时增加
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Authorization")) {
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
                sb.append("Proxy-Authorization: Basic " + Base64.encodeBase64String(proxyAuthorization.getBytes("UTF-8")))
                        .append(CRLF);
            } catch (UnsupportedEncodingException e) {
            }

        }

        sb.append(CRLF);

        return sb.toString();
    }

    public void handleProxyIpIsEmpty(ChannelHandlerContext ctx) {
        String errMsg = "获取代理IP为空，请30s后重试";
        logger.error(errMsg);
        ByteBuf content = Unpooled.copiedBuffer(errMsg, CharsetUtil.UTF_8);
        FullHttpMessage errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
        ctx.channel().writeAndFlush(errorResponse);
        SocksServerUtils.closeOnFlush(ctx.channel());
    }

    void sendForwardReq(final Channel uaChannel,
                        ApnHandlerParams apnHandlerParams,
                        ApnProxyRemote apnProxyRemote,
                        TunnelInstance tunnelInstance,
                        List<HttpContent> httpContentBuffer,
                        Object msg) {
        logger.debug("业务线程开始转发请求");
        long begin = System.currentTimeMillis();
        String remoteAddr = apnProxyRemote.getRemote();

        DirectRelayHandler.RemoteChannelInactiveCallback cb = (remoteChannelCtx, inactiveRemoteAddr) -> {
            logger.info("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
            uaChannel.close();
        };

        final Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup clientEventLoop = new NioEventLoopGroup(1);
        bootstrap
//                .group(uaChannel.eventLoop())
                .group(clientEventLoop)
                .channel(NioSocketChannel.class)

                //todo 修复 close_wait 临时加上
                // -1以及所有<0的数表示socket.close()方法立即返回，但OS底层会将发送缓冲区全部发送到对端。
                // 0表示socket.close()方法立即返回，OS放弃发送缓冲区的数据直接向对端发送RST包，对端收到复位错误。
                // 非0整数值表示调用socket.close()方法的线程被阻塞直到延迟时间到或发送缓冲区中的数据发送完毕，若超时，则对端会收到复位错误。
                .option(ChannelOption.SO_LINGER, 0)
                // 连接心跳检测, 默认2小时12分钟后, 关闭不存活的连接
                .option(ChannelOption.SO_KEEPALIVE, true)
                // 关闭等待所有数据接收完,再发送数据
                .option(ChannelOption.TCP_NODELAY, true)

                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
//                // 这个参数设定的是HTTP连接成功后，等待读取数据或者写数据的最大超时时间，单位为毫秒
//                // 如果设置为0，则表示永远不会超时
//                .option(ChannelOption.SO_TIMEOUT, tunnelInstance.getConnectTimeoutMillis())
//                //todo 临时加上
//                .option(ChannelOption.SO_KEEPALIVE,true)
                // todo 测试错误  failed to allocate 2048 byte(s) of direct memory
//                .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new DirectRelayChannelInitializer(apnHandlerParams, apnProxyRemote, uaChannel, remoteAddr, cb));

        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();

        // set local address
        ChannelFuture remoteConnectFuture = bootstrap.connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort());
        remoteConnectFuture.addListener((ChannelFutureListener) future -> {
            // 执行连接后操作，可能连接成功，也可能失败
            if (future.isSuccess()) {
//                    apnHandlerParams.getRequestMonitor().setSuccess(true);

                long took = System.currentTimeMillis() - begin;
                logger.debug("forward_handler 连接代理IP成功, ip={}, 耗时={} ms", apnProxyRemote.getRemote(), took);
                HttpRequest oldRequest = (HttpRequest) msg;
                logger.debug("forward_handler 重新构造请求之前：{}", oldRequest);
                HttpRequest newRequest = constructRequestForProxyByForward(oldRequest, apnProxyRemote);
                logger.debug("forward_handler 重新构造请求之后：{}", newRequest);
                future.channel().write(newRequest);

                for (HttpContent hc : httpContentBuffer) {
                    future.channel().writeAndFlush(hc);
                }
                httpContentBuffer.clear();
                logger.info("httpContentBuffer size: {}", httpContentBuffer.size());

                // EMPTY_BUFFER 标识会让通道自动关闭
                future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                        .addListener((ChannelFutureListener) future1 -> future1.channel().read());
            } else {
                // todo 如果失败，需要在这里使用新的ip重试（后续改造）
                String errorMsg;
                ConcurrentLinkedQueue<ProxyIp> proxyCfgs = ipPoolScheduleService.getProxyIpPool().get(tunnelInstance.getAlias());
                if (proxyCfgs == null || proxyCfgs.isEmpty()) {
                    long took = System.currentTimeMillis() - begin;
                    errorMsg = "forward_handler连接代理IP失败, ip=" + remoteAddr + ", 耗时=" + took + " ms, 具体原因: ip池为空";
                    logger.error(errorMsg);
                    // send error response
                    HttpMessage errorResponseMsg = HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                    uaChannel.writeAndFlush(errorResponseMsg);
                } else {
                    List<String> ipList = proxyCfgs.stream().filter(Objects::nonNull)
                            .map(item -> item.getHost() + "(" + item.getExpireTime() + ")")
                            .collect(Collectors.toList());
                    long took = System.currentTimeMillis() - begin;
                    String errorMessage = future.cause().getMessage();
                    errorMsg = "forward_handler连接代理IP失败, ip=" + remoteAddr + " , 耗时：" + took + " ms, " +
                            "具体原因: " + errorMessage + ", 此时的ip池列表：" + ipList.toString();
                    logger.error(errorMsg);

                    // send error response
                    HttpMessage errorResponseMsg = HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                    uaChannel.writeAndFlush(errorResponseMsg);
                }

                //todo 临时增加
                requestMonitor.setSuccess(false);
                requestMonitor.setFailReason(errorMsg);
                ReqMonitorUtils.cost(requestMonitor, "sendForwardReq");
                IpMonitorUtils.invoke(requestMonitor, false, "sendForwardReq");

                SocksServerUtils.closeOnFlush(uaChannel);
                httpContentBuffer.clear();
                future.channel().close();

                try {
                    // todo 为了测试 too many files
                    future.channel().closeFuture().sync();
                    clientEventLoop.shutdownGracefully();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    void sendTunnelReq(RequestMonitor requestMonitor,
                       final ChannelHandlerContext ctx,
                       HttpRequest httpRequest,
                       ApnProxyRemote apnProxyRemote,
                       TunnelInstance tunnelInstance) {
        long begin = System.currentTimeMillis();
        Channel uaChannel = ctx.channel();

        // connect remote
        final Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup clientEventLoop = new NioEventLoopGroup(1);
        bootstrap
//                .group(uaChannel.eventLoop())
                .group(clientEventLoop)
                .channel(NioSocketChannel.class)

                //todo 修复 close_wait 临时加上
                // -1以及所有<0的数表示socket.close()方法立即返回，但OS底层会将发送缓冲区全部发送到对端。
                // 0表示socket.close()方法立即返回，OS放弃发送缓冲区的数据直接向对端发送RST包，对端收到复位错误。
                // 非0整数值表示调用socket.close()方法的线程被阻塞直到延迟时间到或发送缓冲区中的数据发送完毕，若超时，则对端会收到复位错误。
                .option(ChannelOption.SO_LINGER, 0)
                // 连接心跳检测, 默认2小时12分钟后, 关闭不存活的连接
                .option(ChannelOption.SO_KEEPALIVE, true)
                // 关闭等待所有数据接收完,再发送数据
                .option(ChannelOption.TCP_NODELAY, true)

                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
                // todo 测试错误  failed to allocate 2048 byte(s) of direct memory
//                .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.AUTO_READ, false)
//                //todo 临时加上
//                .option(ChannelOption.SO_KEEPALIVE,true)
//                .handler(new ChannelInitializer<SocketChannel>() {
//                    @Override
//                    protected void initChannel(SocketChannel ch) throws Exception {
//                        ChannelPipeline pipeline = ch.pipeline();
//                        pipeline.addLast(new LoggingHandler("TUNNEL_LOGGER", LogLevel.INFO));
//                        pipeline.addLast(new TunnelRelayChannelInitializer(requestMonitor, apnProxyRemote, uaChannel));
//                    }
//                });
                .handler(new TunnelRelayChannelInitializer(requestMonitor, apnProxyRemote, uaChannel));

        String remoteHost = apnProxyRemote.getRemoteHost();
        int remotePort = apnProxyRemote.getRemotePort();
        bootstrap.connect(remoteHost, remotePort)
                .addListener((ChannelFutureListener) future1 -> {
                    if (future1.isSuccess()) {
//                            requestMonitor.setSuccess(true);

                        long took = System.currentTimeMillis() - begin;
                        // successfully connect to the original server
                        // send connect success msg to UA
                        logger.info("tunnel_handler 连接代理IP [{}] 成功，耗时: {} ms", apnProxyRemote.getRemote(), took);
                        if (apnProxyRemote.isAppleyRemoteRule()) {
                            ctx.pipeline().remove("codec");
                            ctx.pipeline().remove(ConcurrentLimitHandler.HANDLER_NAME);
                            ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

                            // add relay handler
                            ctx.pipeline().addLast(new TunnelRelayHandler(requestMonitor, "UA --> Remote", future1.channel()));

                            logger.info("tunnel_handler 重新构造请求之前：{}", httpRequest);
                            String newConnectRequest = constructConnectRequestForProxyByTunnel(httpRequest, apnProxyRemote);
                            logger.info("tunnel_handler 重新构造请求之后：{}", newConnectRequest);

                            future1
                                    .channel()
                                    .writeAndFlush(Unpooled.copiedBuffer(newConnectRequest, CharsetUtil.UTF_8))
                                    .addListener((ChannelFutureListener) future2 -> {
                                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                                            future2.channel().read();
                                        }
                                    });

                        } else {
                            logger.info("tunnel_handler 使用本地ip转发");
                            HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "Connection established"));
                            ctx.writeAndFlush(proxyConnectSuccessResponse)
                                    .addListener(
                                            (ChannelFutureListener) future2 -> {
                                                // remove handlers
                                                ctx.pipeline().remove("codec");
//                                                    ctx.pipeline().remove(ConnectionLimitHandler.HANDLER_NAME);
                                                ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

                                                // add relay handler
                                                ctx.pipeline().addLast(new TunnelRelayHandler(requestMonitor, "UA --> " + apnProxyRemote.getRemote(), future1.channel()));
                                            });
                        }

                    } else {
                        String errorMessage = future1.cause().getMessage();
                        long took = System.currentTimeMillis() - begin;
                        logger.info("tunnel_handler 连接代理IP失败，耗时: {} ms", took);
                        if (ctx.channel().isActive()) {
                            ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                    .addListener(ChannelFutureListener.CLOSE);
                        }

                        // todo 临时增加
                        requestMonitor.setSuccess(false);
                        requestMonitor.setFailReason(errorMessage);
                        ReqMonitorUtils.cost(requestMonitor, "sendTunnelReq");
                        IpMonitorUtils.invoke(requestMonitor, false, "sendTunnelReq");


                        try {
                            // todo 为了测试 too many files
                            ctx.channel().closeFuture().sync();
                            clientEventLoop.shutdownGracefully();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }

                });
    }

    class ForwardRequestTask implements Runnable {

        private final Channel uaChannel;
        private final ApnProxyRemote apnProxyRemote;
        private final TunnelInstance tunnelInstance;
        private final List<HttpContent> httpContentBuffer;
        private final Object msg;
        private final ApnHandlerParams apnHandlerParams;

        public ForwardRequestTask(final Channel uaChannel,
                                  ApnHandlerParams apnHandlerParams,
                                  ApnProxyRemote apnProxyRemote,
                                  TunnelInstance tunnelInstance,
                                  List<HttpContent> httpContentBuffer,
                                  Object msg) {
            this.uaChannel = uaChannel;
            this.apnHandlerParams = apnHandlerParams;
            this.apnProxyRemote = apnProxyRemote;
            this.tunnelInstance = tunnelInstance;
            this.httpContentBuffer = httpContentBuffer;
            this.msg = msg;
        }

        @Override
        public void run() {
            sendForwardReq(uaChannel, apnHandlerParams, apnProxyRemote, tunnelInstance, httpContentBuffer, msg);
        }
    }

    class TunnelRequestTask implements Runnable {

        private final ChannelHandlerContext ctx;
        private final HttpRequest httpRequest;
        private final ApnProxyRemote apnProxyRemote;
        private final TunnelInstance tunnelInstance;
        private final RequestMonitor requestMonitor;

        public TunnelRequestTask(RequestMonitor requestMonitor,
                                 final ChannelHandlerContext ctx,
                                 HttpRequest httpRequest,
                                 ApnProxyRemote apnProxyRemote,
                                 TunnelInstance tunnelInstance) {
            this.requestMonitor = requestMonitor;
            this.ctx = ctx;
            this.httpRequest = httpRequest;
            this.apnProxyRemote = apnProxyRemote;
            this.tunnelInstance = tunnelInstance;
        }

        @Override
        public void run() {
            sendTunnelReq(requestMonitor, ctx, httpRequest, apnProxyRemote, tunnelInstance);
        }
    }


}
