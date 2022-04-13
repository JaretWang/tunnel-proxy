package com.dataeye.proxy.apn.service;

import com.alibaba.fastjson.JSON;
import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.handler.ApnProxyRelayHandler;
import com.dataeye.proxy.apn.handler.ApnProxyTunnelHandler;
import com.dataeye.proxy.apn.handler.HttpProxyHandler;
import com.dataeye.proxy.apn.initializer.ApnProxyTunnelChannelInitializer;
import com.dataeye.proxy.apn.initializer.HttpProxyChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyPlainRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.utils.Base64;
import com.dataeye.proxy.apn.utils.HostNamePortUtil;
import com.dataeye.proxy.apn.utils.HttpErrorUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.IpPoolScheduleService;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/7 12:19
 * @description 请求分发
 */

@Service
public class RequestDistributeService {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("RequestDistributeService");

    @Resource
    IpPoolScheduleService ipPoolScheduleService;

    /**
     * 从代理ip池获取ip
     *
     * @param tunnelInstance
     * @return
     */
    @Deprecated
    public ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) {
        String proxyServer = tunnelInstance.getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
        ConcurrentLinkedQueue<ProxyCfg> proxyCfgsQueue = proxyIpPool.get(proxyServer);
        if (proxyCfgsQueue == null || proxyCfgsQueue.isEmpty()) {
            logger.error("实例 {} 对应的代理IP列表为空，需要重新加载", proxyServer);
            ipPoolScheduleService.initSingleServer(tunnelInstance);
            throw new RuntimeException("实例 " + proxyServer + " 对应的代理IP列表为空，需要重新加载");
//            return getProxyConfig(tunnelInstance);
        } else {
            ProxyCfg poll = proxyCfgsQueue.poll();
            if (Objects.nonNull(poll)) {
                logger.info("从队列中获取代理ip的结果：{}", poll);
                // 取了需要再放进去
                proxyCfgsQueue.offer(poll);
                ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
                apPlainRemote.setAppleyRemoteRule(true);
                apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
                apPlainRemote.setRemoteHost(poll.getHost());
                apPlainRemote.setRemotePort(poll.getPort());
                apPlainRemote.setProxyUserName(poll.getUserName());
                apPlainRemote.setProxyPassword(poll.getPassword());
                return apPlainRemote;
            }
            throw new RuntimeException("从队列中 poll 出来的ip为空");
        }
    }

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
        logger.info("转发 connect 请求 -> IP 分配结果：{}", JSON.toJSONString(apnProxyRemote));
        if (Objects.isNull(apnProxyRemote)) {
            handleProxyIpIsEmpty(ctx);
        }

        // 提交代理请求任务
        TunnelRequestTask proxyRequestTask = new TunnelRequestTask(ctx, httpRequest, apnProxyRemote, tunnelInstance);
        ioThreadPool.submit(proxyRequestTask);
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

        // 隧道分配结果
        logger.info("IP 分配结果：{}", JSON.toJSONString(apnProxyRemote));
        if (Objects.isNull(apnProxyRemote)) {
            handleProxyIpIsEmpty(ctx);
        }

        String remoteAddr = apnProxyRemote.getRemote();
        String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
        String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
        int originalPort = HostNamePortUtil.getPort(originalHostHeader, 80);
        logger.info("转发普通请求 to {} for {}:{}", remoteAddr, originalHost, originalPort);

        // 提交代理请求任务
        ForwardRequestTask proxyRequestTask = new ForwardRequestTask(uaChannel, apnProxyRemote, tunnelInstance, httpContentBuffer, msg);
        ioThreadPool.submit(proxyRequestTask);
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

    public void handleProxyIpIsEmpty(ChannelHandlerContext ctx) {
        String errMsg = "获取代理IP为空，请30s后重试";
        logger.error(errMsg);
        ByteBuf content = Unpooled.copiedBuffer(errMsg, CharsetUtil.UTF_8);
        FullHttpMessage errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
        ctx.channel().writeAndFlush(errorResponse);
        SocksServerUtils.closeOnFlush(ctx.channel());
    }

    class ForwardRequestTask implements Runnable {

        private final Channel uaChannel;
        private final ApnProxyRemote apnProxyRemote;
        private final TunnelInstance tunnelInstance;
        private final Bootstrap bootstrap = new Bootstrap();
        private final List<HttpContent> httpContentBuffer;
        private final Object msg;

        public ForwardRequestTask(final Channel uaChannel,
                                  ApnProxyRemote apnProxyRemote,
                                  TunnelInstance tunnelInstance,
                                  List<HttpContent> httpContentBuffer,
                                  Object msg) {
            this.uaChannel = uaChannel;
            this.apnProxyRemote = apnProxyRemote;
            this.tunnelInstance = tunnelInstance;
            this.httpContentBuffer = httpContentBuffer;
            this.msg = msg;
        }

        @Override
        public void run() {
            logger.info("业务线程开始转发请求");
            long begin = System.currentTimeMillis();
            String remoteAddr = apnProxyRemote.getRemote();

            HttpProxyHandler.RemoteChannelInactiveCallback cb = (remoteChannelCtx, inactiveRemoteAddr) -> {
                logger.debug("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
                uaChannel.close();
            };

            bootstrap
                    .group(uaChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new HttpProxyChannelInitializer(apnProxyRemote, uaChannel, remoteAddr, cb));

            // set local address
            logger.info("代理ip: {}", apnProxyRemote.getRemote());
            ChannelFuture remoteConnectFuture = bootstrap.connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort());

            remoteConnectFuture.addListener((ChannelFutureListener) future -> {
                // 执行连接后操作，可能连接成功，也可能失败
                if (future.isSuccess()) {
                    long took = System.currentTimeMillis() - begin;
                    logger.info("forward_handler 连接代理IP成功, 耗时：{} ms", took);
                    HttpRequest newRequest = constructRequestForProxyByForward((HttpRequest) msg, apnProxyRemote);
                    future.channel().write(newRequest);

                    for (HttpContent hc : httpContentBuffer) {
                        future.channel().writeAndFlush(hc);
                    }
                    httpContentBuffer.clear();
                    logger.info("httpContentBuffer size: {}", httpContentBuffer.size());

                    // EMPTY_BUFFER 标识会让通道自动关闭
                    future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                            .addListener((ChannelFutureListener) future1 -> future1.channel().read());
                    logger.info("forward_handler 写入缓存的一次http请求, 耗时：{} ms", took);
                } else {
                    ConcurrentLinkedQueue<ProxyCfg> proxyCfgs = ipPoolScheduleService.getProxyIpPool().get(tunnelInstance.getAlias());
                    if (proxyCfgs == null || proxyCfgs.isEmpty()) {
                        long took = System.currentTimeMillis() - begin;
                        String errorMsg = "forward_handler 连接代理IP [" + remoteAddr + "] 失败, " +
                                "耗时：" + took + " ms, 具体原因: " + tunnelInstance.getAlias() + " 对应的ip池为空";
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
                        String errorMsg = "forward_handler 连接代理IP [" + remoteAddr + "] 失败, " +
                                "耗时：" + took + " ms, " +
                                "具体原因: " + errorMessage + ", 此时的ip池列表：" + ipList.toString();
                        logger.error(errorMsg);
                        // send error response
                        HttpMessage errorResponseMsg = HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                        uaChannel.writeAndFlush(errorResponseMsg);
                    }
                    httpContentBuffer.clear();
                    future.channel().close();
                }
            });
        }
    }

    class TunnelRequestTask implements Runnable {

        private final ChannelHandlerContext ctx;
        private final HttpRequest httpRequest;
        private final ApnProxyRemote apnProxyRemote;
        private final TunnelInstance tunnelInstance;
        private final Bootstrap bootstrap = new Bootstrap();

        public TunnelRequestTask(final ChannelHandlerContext ctx,
                                 HttpRequest httpRequest,
                                 ApnProxyRemote apnProxyRemote,
                                 TunnelInstance tunnelInstance) {
            this.ctx = ctx;
            this.httpRequest = httpRequest;
            this.apnProxyRemote = apnProxyRemote;
            this.tunnelInstance = tunnelInstance;
        }

        @Override
        public void run() {
            long begin = System.currentTimeMillis();
            Channel uaChannel = ctx.channel();

            // connect remote
            bootstrap
                    .group(uaChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ApnProxyTunnelChannelInitializer(apnProxyRemote, uaChannel));

            String remoteHost = apnProxyRemote.getRemoteHost();
            int remotePort = apnProxyRemote.getRemotePort();
            logger.info("代理ip: {}:{}", remoteHost, remotePort);

            bootstrap.connect(remoteHost, remotePort)
                    .addListener((ChannelFutureListener) future1 -> {
                        if (future1.isSuccess()) {
                            long took = System.currentTimeMillis() - begin;
                            // successfully connect to the original server
                            // send connect success msg to UA
                            logger.info("tunnel_handler 连接代理IP成功，耗时: {} ms", took);
                            if (apnProxyRemote.isAppleyRemoteRule()) {
                                ctx.pipeline().remove("codec");
                                ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

                                // add relay handler
                                ctx.pipeline().addLast(new ApnProxyRelayHandler("UA --> Remote", future1.channel()));

                                String newConnectRequest = constructConnectRequestForProxyByTunnel(httpRequest, apnProxyRemote);
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
                                                    ctx.pipeline().remove(
                                                            ApnProxyTunnelHandler.HANDLER_NAME);

                                                    // add relay handler
                                                    ctx.pipeline().addLast(new ApnProxyRelayHandler("UA --> " + apnProxyRemote.getRemote(), future1.channel()));
                                                });
                            }

                        } else {
                            long took = System.currentTimeMillis() - begin;
                            logger.info("tunnel_handler 连接代理IP失败，耗时: {} ms", took);
                            if (ctx.channel().isActive()) {
                                ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                        .addListener(ChannelFutureListener.CLOSE);
                            }
                        }
                    });
        }

    }

}
