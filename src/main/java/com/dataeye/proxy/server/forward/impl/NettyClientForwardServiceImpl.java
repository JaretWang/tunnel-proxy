package com.dataeye.proxy.server.forward.impl;

import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.cons.Log;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.server.forward.RequestForwardService;
import com.dataeye.proxy.server.handler.ApnProxySchemaHandler;
import com.dataeye.proxy.server.handler.ApnProxyTunnelHandler;
import com.dataeye.proxy.server.handler.ConcurrentLimitHandler;
import com.dataeye.proxy.server.handler.TunnelRelayHandler;
import com.dataeye.proxy.server.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.server.initializer.TunnelRelayChannelInitializer;
import com.dataeye.proxy.utils.HostNamePortUtils;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import okhttp3.Credentials;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * @author jaret
 * @date 2022/11/2 10:58
 * @description
 */
@Service
public class NettyClientForwardServiceImpl implements RequestForwardService {

    private static final boolean useProxy = false;

    @Override
    public void sendHttp(ChannelHandlerContext ctx, ProxyIp proxyIp, ApnHandlerParams apnHandlerParams, FullHttpRequest fullHttpRequest, String handler) {

    }

    @Override
    public void sendhttps(ChannelHandlerContext ctx, ProxyIp proxyIp, ApnHandlerParams apnHandlerParams, FullHttpRequest fullHttpRequest, String handler) {
        if (proxyIp == null) {
            return;
        }
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
        sendHttpsByNettyClient(Log.SERVER, ctx, fullHttpRequest, requestMonitor, proxyIp, tunnelInstance);
    }

    @Override
    public void sendSocks5(ChannelHandlerContext ctx, ProxyIp proxyIp, ApnHandlerParams apnHandlerParams, FullHttpRequest fullHttpRequest, String handler) {

    }


    public void sendHttpsByNettyClient(Logger logger, final ChannelHandlerContext ctx,
                                       FullHttpRequest httpRequest,
                                       RequestMonitor requestMonitor,
                                       ProxyIp proxyIp,
                                       TunnelInstance tunnelInstance) {
        long begin = System.currentTimeMillis();
        Channel uaChannel = ctx.channel();

        final Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(uaChannel.eventLoop())
                .channel(NioSocketChannel.class)
                // 用于修复 close_wait 过多的问题
                // -1以及所有<0的数表示socket.close()方法立即返回，但OS底层会将发送缓冲区全部发送到对端。
                // 0表示socket.close()方法立即返回，OS放弃发送缓冲区的数据直接向对端发送RST包，对端收到复位错误。
                // 非0整数值表示调用socket.close()方法的线程被阻塞直到延迟时间到或发送缓冲区中的数据发送完毕，若超时，则对端会收到复位错误。
                .option(ChannelOption.SO_LINGER, 0)
                // 连接心跳检测, 默认2小时12分钟后, 关闭不存活的连接
                .option(ChannelOption.SO_KEEPALIVE, true)
                // 关闭等待所有数据接收完,再发送数据
                .option(ChannelOption.TCP_NODELAY, true)
                // 连接超时设置
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
                // 关闭连接成功后自动读取,因为需要构造一个模拟请求,重新发送
                .option(ChannelOption.AUTO_READ, false)
                // fixed failed to allocate 2048 byte(s) of direct memory
                // .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .handler(new TunnelRelayChannelInitializer(requestMonitor, proxyIp, uaChannel, tunnelInstance));
        if (1==1) {
            bootstrap.connect(proxyIp.getHost(), proxyIp.getPort())
                    .addListener((ChannelFutureListener) future1 -> {
                        long took = System.currentTimeMillis() - begin;
                        String status = future1.isSuccess() ? "成功" : "失败, cause=" + future1.cause().getMessage();
                        logger.info("tunnel_handler, 连接状态={}，耗时={} ms, proxy={}", status, took, proxyIp.getRemote());
                        connectByProxyIp(logger, requestMonitor, ctx, httpRequest, proxyIp, future1);
                    });
        } else {
//            String originalHostHeader = httpRequest.headers().get(HttpHeaderNames.HOST);
            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtils.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtils.getPort(originalHostHeader, 443);
            bootstrap.connect(originalHost, originalPort)
                    .addListener((ChannelFutureListener) future1 -> {
                        long took = System.currentTimeMillis() - begin;
                        String status = future1.isSuccess() ? "成功" : "失败, cause=" + future1.cause().getMessage();
                        logger.info("tunnel_handler, 连接状态={}，耗时={} ms", status, took);
                        connectByLocal(logger, requestMonitor, ctx, httpRequest, proxyIp, future1);
                    });
        }
    }

    void connectByLocal(Logger logger, RequestMonitor requestMonitor,
                        final ChannelHandlerContext ctx,
                        FullHttpRequest httpRequest,
                        ProxyIp proxyIp,
                        ChannelFuture future1) {
        if (!future1.isSuccess()) {
            handleConnectError(logger, requestMonitor, ctx, httpRequest, proxyIp, future1);
            return;
        }
        HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                new HttpResponseStatus(200, "Connection established"));
        System.out.println("响应连接建立：" + proxyConnectSuccessResponse.toString());
        ctx.writeAndFlush(proxyConnectSuccessResponse)
                .addListener((ChannelFutureListener) future2 -> {
//                    System.out.println("tunnel_handler remove之前=" + ctx.pipeline().toMap().size());
//                    ctx.pipeline().toMap().keySet().forEach(System.out::println);
                    // remove handlers
                    removeHandler(ApnProxyServerChannelInitializer.SERVER_CODEC_NAME, ctx);
                    removeHandler(ApnProxyServerChannelInitializer.SERVER_REQUEST_AGG_NAME, ctx);
                    removeHandler(ApnProxyServerChannelInitializer.SERVER_REQUEST_DECOMPRESSOR_NAME, ctx);
                    //removeHandler(ApnProxyServerChannelInitializer.SERVER_BANDWIDTH_MONITOR_NAME,ctx);
                    removeHandler(ConcurrentLimitHandler.HANDLER_NAME, ctx);
                    removeHandler(ApnProxySchemaHandler.HANDLER_NAME, ctx);
                    removeHandler(ApnProxyTunnelHandler.HANDLER_NAME, ctx);
                    // add relay handler
                    ctx.pipeline().addLast(new TunnelRelayHandler(requestMonitor, "UA --> " + proxyIp.getIpAddr(), future1.channel()));
                });
    }


    void connectByProxyIp(Logger logger, RequestMonitor requestMonitor,
                          final ChannelHandlerContext ctx,
                          FullHttpRequest httpRequest,
                          ProxyIp proxyIp,
                          ChannelFuture future1) {
        if (!future1.isSuccess()) {
            handleConnectError(logger, requestMonitor, ctx, httpRequest, proxyIp, future1);
        }
        // remove之前
        //System.out.println("tunnel_handler remove之前=" + ctx.pipeline().toMap().size());
        //ctx.pipeline().toMap().keySet().forEach(System.out::println);
        // todo console总是会报没有这个handler
//                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_IDLE_STATE_NAME);
//                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_IDLE_HANDLER_NAME);
        removeHandler(ApnProxyServerChannelInitializer.SERVER_CODEC_NAME, ctx);
        removeHandler(ApnProxyServerChannelInitializer.SERVER_REQUEST_AGG_NAME, ctx);
        removeHandler(ApnProxyServerChannelInitializer.SERVER_REQUEST_DECOMPRESSOR_NAME, ctx);
//                    removeHandler(ApnProxyServerChannelInitializer.SERVER_BANDWIDTH_MONITOR_NAME,ctx);
        removeHandler(ConcurrentLimitHandler.HANDLER_NAME, ctx);
        removeHandler(ApnProxySchemaHandler.HANDLER_NAME, ctx);
        removeHandler(ApnProxyTunnelHandler.HANDLER_NAME, ctx);

        // remove之后
        //System.out.println("tunnel_handler remove之后=" + ctx.pipeline().toMap().size());
        //ctx.pipeline().toMap().keySet().forEach(System.out::println);
//                    ctx.pipeline().addLast("client_idlestate", new IdleStateHandler(
//                            ApnProxyServerChannelInitializer.CLIENT_READ_IDLE_TIME,
//                            ApnProxyServerChannelInitializer.CLIENT_WRITE_IDLE_TIME,
//                            ApnProxyServerChannelInitializer.CLIENT_ALL_IDLE_TIME, TimeUnit.SECONDS));
//                    ctx.pipeline().addLast("client_idlehandler", new IdleHandler());
//                    ctx.pipeline().addLast("client_read_timeout", new ReadTimeoutHandler(tunnelInstance.getReadTimeoutSeconds()));
//                    ctx.pipeline().addLast("client_write_timeout", new WriteTimeoutHandler(tunnelInstance.getWriteTimeoutSeconds()));
//                    ctx.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
//                        ctx.pipeline().addLast(new HttpClientCodec());
        ctx.pipeline().addLast(new TunnelRelayHandler(requestMonitor, "UA --> " + proxyIp.getIpAddr(), future1.channel()));

        logger.info("tunnel_handler 重新构造请求之前：{}{}", System.lineSeparator(), httpRequest);
        String newConnectRequest = constructReqForConnect(httpRequest, proxyIp);
        //String newConnectRequest = buildReqForConnect(httpRequest, apnProxyRemote);
        logger.info("tunnel_handler 重新构造请求之后：size={}", newConnectRequest.getBytes().length);
        logger.debug("tunnel_handler 重新构造请求之后：{}{}", System.lineSeparator(), newConnectRequest);

        ByteBuf reqContent = Unpooled.copiedBuffer(newConnectRequest, CharsetUtil.UTF_8);
        // ReferenceCountUtil.releaseLater() will keep the reference of buf,
        // and then release it when the test thread is terminated.
        // ReferenceCountUtil.releaseLater(reqContent);
        future1.channel()
                .writeAndFlush(reqContent)
                .addListener((ChannelFutureListener) future2 -> {
                    if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                        future2.channel().read();
                    }
                });
    }

    void handleConnectError(Logger logger, RequestMonitor requestMonitor,
                            final ChannelHandlerContext ctx,
                            FullHttpRequest httpRequest,
                            ProxyIp proxyIp,
                            ChannelFuture future1) {
        String errorMessage = future1.cause().getMessage();

        // 监控统计
        ReqMonitorUtils.error(requestMonitor, "sendTunnelReq", errorMessage);
        IpMonitorUtils.error(requestMonitor, "sendTunnelReq", errorMessage);

        SocksServerUtils.errorHttpResp(ctx.channel(), "tunnel connect ");

        // Close the connection if the connection attempt has failed.
//        ctx.channel().writeAndFlush(new DefaultHttpResponse(httpRequest.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR));
//                        ChannelPromise channelPromise = ctx.channel().newPromise();
//                        channelPromise.setFailure(new IOException("connect ip fail"));

        // 关闭资源
        ctx.channel().close();
        future1.channel().close();
//                        SocksServerUtils.closeOnFlush(ctx.channel());
//                        SocksServerUtils.closeOnFlush(future1.channel());
    }

    void removeHandler(String name, ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (pipeline.names().contains(name)) {
            pipeline.remove(name);
        }
    }

    public String constructReqForConnect(HttpRequest httpRequest,
                                         ProxyIp proxyIp) {
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
            if (StringUtils.equalsIgnoreCase(headerName, "Connection")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Authorization")) {
                continue;
            }
            for (String headerValue : httpRequest.headers().getAll(headerName)) {
                sb.append(headerName).append(": ").append(headerValue).append(CRLF);
            }
        }
//        sb.append("Connection").append(": ").append("close").append(CRLF);
//        sb.append("Proxy-Connection").append(": ").append("close").append(CRLF);

        String userName = proxyIp.getUserName();
        String password = proxyIp.getPassword();
        if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
            sb.append("Proxy-Authorization: ").append(Credentials.basic(userName, password)).append(CRLF);
        }

        sb.append(CRLF);
        return sb.toString();
    }

    DefaultFullHttpResponse buildConnectionResp() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                new HttpResponseStatus(200, "Connection established"));
    }

    //    /**
//     * 转发普通请求
//     *
//     * @param uaChannel         源通道
//     * @param apnHandlerParams  handler相关参数
//     * @param proxyIp           代理ip
//     * @param tunnelInstance    隧道实例
//     * @param httpContentBuffer http content缓存
//     * @param msg               传递的源数据
//     */
//    public void forwardCommonReq(final Channel uaChannel,
//                                 ApnHandlerParams apnHandlerParams,
//                                 ProxyIp proxyIp,
//                                 TunnelInstance tunnelInstance,
//                                 List<HttpContent> httpContentBuffer,
//                                 Object msg) {
//        long begin = System.currentTimeMillis();
//        String remoteAddr = proxyIp.getRemote();
//        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
//        DirectRelayHandler.RemoteChannelInactiveCallback cb = (remoteChannelCtx, inactiveRemoteAddr) -> {
//            logger.debug("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
//            uaChannel.close();
//        };
//
//        final Bootstrap bootstrap = new Bootstrap();
//        bootstrap
//                .group(uaChannel.eventLoop())
//                .channel(NioSocketChannel.class)
//                // 用于修复 close_wait 过多的问题
//                // -1以及所有<0的数表示socket.close()方法立即返回，但OS底层会将发送缓冲区全部发送到对端。
//                // 0表示socket.close()方法立即返回，OS放弃发送缓冲区的数据直接向对端发送RST包，对端收到复位错误。
//                // 非0整数值表示调用socket.close()方法的线程被阻塞直到延迟时间到或发送缓冲区中的数据发送完毕，若超时，则对端会收到复位错误。
//                .option(ChannelOption.SO_LINGER, 0)
//                // 连接心跳检测, 默认2小时12分钟后, 关闭不存活的连接
//                .option(ChannelOption.SO_KEEPALIVE, true)
//                // 关闭等待所有数据接收完,再发送数据
//                .option(ChannelOption.TCP_NODELAY, true)
//                // 连接超时设置
//                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
//                // 关闭连接成功后自动读取,因为需要构造一个模拟请求,重新发送
//                .option(ChannelOption.AUTO_READ, false)
////                // 这个参数设定的是HTTP连接成功后，等待读取数据或者写数据的最大超时时间，单位为毫秒
////                // 如果设置为0，则表示永远不会超时
////                .option(ChannelOption.SO_TIMEOUT, tunnelInstance.getConnectTimeoutMillis())
////                // fixed failed to allocate 2048 byte(s) of direct memory
////                .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
//                .handler(new DirectRelayChannelInitializer(apnHandlerParams, proxyIp, uaChannel, remoteAddr, cb))
//                .connect(proxyIp.getHost(), proxyIp.getPort())
//                .addListener((ChannelFutureListener) future -> {
//                    long took = System.currentTimeMillis() - begin;
//                    // 执行连接后操作，可能连接成功，也可能失败
//                    if (future.isSuccess()) {
//                        logger.debug("forward_handler 连接代理IP成功, ip={}, 耗时={} ms", proxyIp.getRemote(), took);
//                        HttpRequest oldRequest = (HttpRequest) msg;
//                        logger.debug("forward_handler 构造请求之前：{}", oldRequest);
//                        HttpRequest newRequest = constructReqForCommon(oldRequest, proxyIp);
//                        logger.debug("forward_handler 构造请求之后：{}", newRequest);
//                        future.channel().write(newRequest);
//
//                        logger.debug("httpContentBuffer size={}", httpContentBuffer.size());
//                        for (HttpContent hc : httpContentBuffer) {
//                            future.channel().writeAndFlush(hc);
//                        }
//                        httpContentBuffer.clear();
//
//                        // EMPTY_BUFFER 标识数据已经写完,会自动关闭
//                        future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
//                                .addListener((ChannelFutureListener) future1 -> future1.channel().read());
//                    } else {
//                        // todo 如果失败，需要在这里使用新的ip重试（后续改造）
//                        String errorMsg;
////                        ConcurrentLinkedQueue<ProxyIp> proxyCfgs = ipPoolScheduleService.getProxyIpPool().get(tunnelInstance.getAlias());
////                        ConcurrentLinkedQueue<ProxyIp> proxyCfgs = ipSelector.getProxyIpPool().get(tunnelInstance.getAlias());
//                        // todo 暂时关闭掉 整理代码
//                        ConcurrentLinkedQueue<ProxyIp> proxyCfgs = null;
//                        if (proxyCfgs == null || proxyCfgs.isEmpty()) {
//                            errorMsg = "forward_handler连接代理IP失败, ip=" + remoteAddr + ", 具体原因: ip池为空";
//                        } else {
//                            List<String> ipList = proxyCfgs.stream().filter(Objects::nonNull)
//                                    .map(item -> item.getHost() + "(" + item.getExpireTime() + ")")
//                                    .collect(Collectors.toList());
//                            String errorMessage = future.cause().getMessage();
//                            errorMsg = "forward_handler连接代理IP失败, ip=" + remoteAddr + " , 耗时：" + took + " ms, " +
//                                    "具体原因: " + errorMessage + ", 此时的ip池列表：" + ipList.toString();
//                        }
//
//                        // send error response
//                        logger.error(errorMsg);
//                        HttpMessage errorResponseMsg = HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
//                        uaChannel.writeAndFlush(errorResponseMsg);
//
//                        // 统计
//                        ReqMonitorUtils.error(requestMonitor, "sendForwardReq", errorMsg);
//                        IpMonitorUtils.error(requestMonitor, "sendForwardReq", errorMsg);
//
//                        // 关闭资源
//                        SocksServerUtils.closeOnFlush(uaChannel);
//                        httpContentBuffer.clear();
//                        future.channel().close();
//                    }
//                });
//    }

}
