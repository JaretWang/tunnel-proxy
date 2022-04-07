package com.dataeye.proxy.apn.service;

import com.alibaba.fastjson.JSON;
import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.cons.ApnProxyConstants;
import com.dataeye.proxy.apn.handler.ApnProxyPreHandler;
import com.dataeye.proxy.apn.handler.ApnProxyRelayHandler;
import com.dataeye.proxy.apn.handler.ApnProxyTunnelHandler;
import com.dataeye.proxy.apn.handler.HttpProxyHandler;
import com.dataeye.proxy.apn.initializer.ApnProxyTunnelChannelInitializer;
import com.dataeye.proxy.apn.initializer.HttpProxyChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyLocalAddressChooser;
import com.dataeye.proxy.apn.remotechooser.ApnProxyPlainRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.utils.Base64;
import com.dataeye.proxy.apn.utils.HostNamePortUtil;
import com.dataeye.proxy.apn.utils.HttpErrorUtil;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.IpPoolScheduleService;
import com.dataeye.proxy.tunnel.TunnelProxyServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author jaret
 * @date 2022/4/7 12:19
 * @description 请求分发
 */

@Service
public class RequestDistributeService {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("RequestDistributeService");

    @Resource(name = "cpuThreadPool")
    private ThreadPoolTaskExecutor ioThreadPool;
    @Resource
    IpPoolScheduleService ipPoolScheduleService;

    /**
     * 从代理ip池获取ip
     *
     * @param tunnelInstance
     * @return
     */
    public ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) {
        String proxyServer = tunnelInstance.getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyCfg>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
        ConcurrentLinkedQueue<ProxyCfg> proxyCfgsQueue = proxyIpPool.get(proxyServer);
        if (proxyCfgsQueue == null || proxyCfgsQueue.isEmpty()) {
            logger.error("实例 {} 对应的代理IP列表为空，需要重新加载", proxyServer);
            ipPoolScheduleService.initSingleServer(tunnelInstance);
            // todo 有可能栈溢出
            return getProxyConfig(tunnelInstance);
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
     * @param ctx
     * @param httpRequest
     * @param apnProxyRemote
     * @param tunnelInstance
     */
    public void sendRequestByTunnel(final ChannelHandlerContext ctx, HttpRequest httpRequest, ApnProxyRemote apnProxyRemote, TunnelInstance tunnelInstance) {
        // 隧道分配结果
        ApnProxyRemote proxyConfig = getProxyConfig(tunnelInstance);
        logger.info("IP 分配结果：{}", JSON.toJSONString(proxyConfig));
        // 提交代理请求任务
        TunnelRequestTask proxyRequestTask = new TunnelRequestTask(ctx, httpRequest, apnProxyRemote, tunnelInstance);
        ioThreadPool.submit(proxyRequestTask);
    }

    /**
     * 转发普通请求
     *
     * @param uaChannel
     * @param remoteChannel
     * @param httpRequest
     * @param apnProxyRemote
     * @param tunnelInstance
     * @param httpContentBuffer
     * @param remoteChannelMap
     * @param msg
     */
    public void sendRequestByForward(final Channel uaChannel, Channel remoteChannel,
                                     HttpRequest httpRequest, ApnProxyRemote apnProxyRemote,
                                     TunnelInstance tunnelInstance, List<HttpContent> httpContentBuffer,
                                     Map<String, Channel> remoteChannelMap, Object msg) {
        // 隧道分配结果
        ApnProxyRemote proxyConfig = getProxyConfig(tunnelInstance);
        logger.info("IP 分配结果：{}", JSON.toJSONString(proxyConfig));
        // 提交代理请求任务
        ForwardRequestTask proxyRequestTask = new ForwardRequestTask(uaChannel, remoteChannel,
                httpRequest, apnProxyRemote, tunnelInstance, httpContentBuffer, remoteChannelMap, msg);
        ioThreadPool.submit(proxyRequestTask);
    }


    class ForwardRequestTask implements Runnable {

        private final Channel uaChannel;
        private Channel remoteChannel;
        private final HttpRequest httpRequest;
        private final ApnProxyRemote apnProxyRemote;
        private final TunnelInstance tunnelInstance;
        private final Bootstrap bootstrap = new Bootstrap();
        private String remoteAddr;
        private final List<HttpContent> httpContentBuffer;
        private final Map<String, Channel> remoteChannelMap;
        private final Object msg;


        public ForwardRequestTask(final Channel uaChannel, Channel remoteChannel,
                                  HttpRequest httpRequest, ApnProxyRemote apnProxyRemote,
                                  TunnelInstance tunnelInstance, List<HttpContent> httpContentBuffer,
                                  Map<String, Channel> remoteChannelMap, Object msg) {
            this.uaChannel = uaChannel;
            this.remoteChannel = remoteChannel;
            this.httpRequest = httpRequest;
            this.apnProxyRemote = apnProxyRemote;
            this.tunnelInstance = tunnelInstance;
            this.httpContentBuffer = httpContentBuffer;
            this.remoteChannelMap = remoteChannelMap;
            this.msg = msg;
        }

        @Override
        public void run() {
            logger.info("业务线程开始转发请求");
            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtil.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtil.getPort(originalHostHeader, 80);
            remoteAddr = apnProxyRemote.getRemote();

            HttpProxyHandler.RemoteChannelInactiveCallback cb = (remoteChannelCtx, inactiveRemoteAddr) -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
                }
                uaChannel.close();
            };

            logger.info("client 发起代理请求");
            bootstrap
                    .group(uaChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new HttpProxyChannelInitializer(apnProxyRemote, uaChannel, remoteAddr, cb));

            // set local address
            String remoteHost = apnProxyRemote.getRemoteHost();
            String ip = ApnProxyLocalAddressChooser.choose(remoteHost);
            if (StringUtils.isNotBlank(ip)) {
                logger.info("本地地址: {}", ip);
                bootstrap.localAddress(new InetSocketAddress(ip, 0));
            }
            int remotePort = apnProxyRemote.getRemotePort();
            logger.info("代理ip: {}:{}", remoteHost, remotePort);

            ChannelFuture remoteConnectFuture = bootstrap.connect(remoteHost, remotePort);

            remoteChannel = remoteConnectFuture.channel();
            remoteChannel.attr(ApnProxyConstants.REQUST_URL_ATTRIBUTE_KEY).set(httpRequest.getUri());
            remoteChannelMap.put(remoteAddr, remoteChannel);

            remoteConnectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    logger.info("转发普通请求，连接成功");

                    HttpRequest newRequest = constructRequestForProxyByForward((HttpRequest) msg, apnProxyRemote);
                    future.channel().write(newRequest);

                    logger.info("将缓存的 HttpContent 写回通道");
                    for (HttpContent hc : httpContentBuffer) {
                        future.channel().writeAndFlush(hc);
                    }
                    httpContentBuffer.clear();

                    // EMPTY_BUFFER 会自动关闭
                    future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                            .addListener((ChannelFutureListener) future1 -> future1.channel().read());
                } else {
                    String errorMsg = "转发普通请求，连接远程地址 [" + remoteAddr + "] 失败";
                    logger.error(errorMsg);
                    // send error response
                    HttpMessage errorResponseMsg = HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                    uaChannel.writeAndFlush(errorResponseMsg);
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
                                 HttpRequest httpRequest, ApnProxyRemote apnProxyRemote,
                                 TunnelInstance tunnelInstance) {
            this.ctx = ctx;
            this.httpRequest = httpRequest;
            this.apnProxyRemote = apnProxyRemote;
            this.tunnelInstance = tunnelInstance;
        }

        @Override
        public void run() {
            Channel uaChannel = ctx.channel();

            // connect remote
            bootstrap
                    .group(uaChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelInstance.getConnectTimeoutMillis())
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ApnProxyTunnelChannelInitializer(apnProxyRemote, uaChannel));

            // set local address
//            if (StringUtils.isNotBlank(ApnProxyLocalAddressChooser.choose(apnProxyRemote
//                    .getRemoteHost()))) {
//                bootstrap.localAddress(new InetSocketAddress((ApnProxyLocalAddressChooser
//                        .choose(apnProxyRemote.getRemoteHost())), 0));
//            }
            String remoteHost = apnProxyRemote.getRemoteHost();
            String ip = ApnProxyLocalAddressChooser.choose(remoteHost);
            if (StringUtils.isNotBlank(ip)) {
                logger.info("本地地址: {}", ip);
                bootstrap.localAddress(new InetSocketAddress(ip, 0));
            }
            int remotePort = apnProxyRemote.getRemotePort();
            logger.info("代理ip: {}:{}", remoteHost, remotePort);

            bootstrap.connect(remoteHost, remotePort)
                    .addListener((ChannelFutureListener) future1 -> {
                        if (future1.isSuccess()) {
                            // successfully connect to the original server
                            // send connect success msg to UA
                            logger.info("tunnel_handler 连接成功");
                            if (apnProxyRemote.isAppleyRemoteRule()) {
                                logger.info("tunnel_handler 使用代理ip转发");
                                ctx.pipeline().remove("codec");
                                ctx.pipeline().remove(ApnProxyPreHandler.HANDLER_NAME);
                                ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

                                // add relay handler
                                ctx.pipeline().addLast(new ApnProxyRelayHandler("UA --> Remote", future1.channel()));

                                future1
                                        .channel()
                                        .writeAndFlush(Unpooled.copiedBuffer(constructConnectRequestForProxyByTunnel(httpRequest, apnProxyRemote), CharsetUtil.UTF_8))
                                        .addListener((ChannelFutureListener) future2 -> {
                                            if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                                                future2.channel().read();
                                            }
                                        });

                            } else {
                                logger.info("tunnel_handler 使用本地ip转发");
                                HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(
                                        HttpVersion.HTTP_1_1, new HttpResponseStatus(200,
                                        "Connection established"));
                                ctx.writeAndFlush(proxyConnectSuccessResponse)
                                        .addListener(
                                                (ChannelFutureListener) future2 -> {
                                                    // remove handlers
                                                    ctx.pipeline().remove("codec");
                                                    ctx.pipeline().remove(ApnProxyPreHandler.HANDLER_NAME);
                                                    ctx.pipeline().remove(
                                                            ApnProxyTunnelHandler.HANDLER_NAME);

                                                    // add relay handler
                                                    ctx.pipeline().addLast(new ApnProxyRelayHandler("UA --> " + apnProxyRemote.getRemote(), future1.channel()));
                                                });
                            }

                        } else {
                            logger.info("tunnel_handler 连接失败");
                            if (ctx.channel().isActive()) {
                                ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                        .addListener(ChannelFutureListener.CLOSE);
                            }
                        }
                    });
        }

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

}
