package com.dataeye.proxy.server.service;

import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.cons.Log;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.server.forward.impl.NettyClientForwardServiceImpl;
import com.dataeye.proxy.server.handler.*;
import com.dataeye.proxy.server.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.server.initializer.TunnelRelayChannelInitializer;
import com.dataeye.proxy.utils.ApnProxySSLContextFactory;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.OkHttpTool;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author jaret
 * @date 2022/4/7 12:19
 * @description 请求分发
 */
@Service
public class RequestDistributeService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    @Autowired
    NettyClientForwardServiceImpl nettyClientForwardService;
    @Autowired
    OkHttpTool okHttpTool;

    public void sendHttps(ChannelHandlerContext ctx,
                          FullHttpRequest fullHttpRequest,
                          ApnHandlerParams apnHandlerParams,
                          ProxyIp proxyIp) {
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        TunnelInstance tunnelInstance = apnHandlerParams.getTunnelInstance();
        nettyClientForwardService.sendHttpsByNettyClient(Log.SERVER, ctx, fullHttpRequest, requestMonitor, proxyIp, tunnelInstance);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------------------------

    public HttpRequest constructReqForCommon(HttpRequest httpRequest,
                                             ProxyIp proxyIp) {

        String uri = httpRequest.getUri();

//        if (!apnProxyRemote.isAppleyRemoteRule()) {
//            uri = this.getPartialUrl(uri);
//        }

        HttpRequest _httpRequest = new DefaultHttpRequest(httpRequest.getProtocolVersion(),
                httpRequest.getMethod(), uri);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
            // 放开请求，更改一下请求头
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

//        _httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//         _httpRequest.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.IDENTITY);
        // 更改长连接为短链接
        _httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);

        String userName = proxyIp.getUserName();
        String password = proxyIp.getPassword();
        if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
            _httpRequest.headers().set("Proxy-Authorization", Credentials.basic(userName, password));
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

    public String buildReqForConnect(HttpRequest httpRequest,
                                     ProxyIp proxyIp) {
        String lineSeparator = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(httpRequest.method().name())
                .append(" ").append(httpRequest.uri()).append(" ")
                .append(httpRequest.protocolVersion().text())
                .append(lineSeparator);

        for (Map.Entry<String, String> next : httpRequest.headers()) {
            String headerName = next.getKey();
            String headerValue = next.getValue();
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, "Connection")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Authorization")) {
                continue;
            }
            sb.append(headerName).append(": ").append(headerValue).append(lineSeparator);
        }
//        sb.append("Connection").append(": ").append("close").append(lineSeparator);
//        sb.append("Proxy-Connection").append(": ").append("close").append(lineSeparator);

        String proxyUserName = proxyIp.getUserName();
        String proxyPassword = proxyIp.getPassword();
        if (StringUtils.isNotBlank(proxyUserName) && StringUtils.isNotBlank(proxyPassword)) {
            sb.append("Proxy-Authorization: ").append(Credentials.basic(proxyUserName, proxyPassword)).append(lineSeparator);
        }
        sb.append(lineSeparator);
        return sb.toString();
    }


    public void handleProxyIpIsEmpty(ChannelHandlerContext ctx) {
        String errMsg = "no proxy ip available";
        logger.error(errMsg);
        ByteBuf content = Unpooled.copiedBuffer(errMsg, CharsetUtil.UTF_8);
        DefaultFullHttpResponse errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
        ctx.channel().writeAndFlush(errorResponse);
        SocksServerUtils.closeOnFlush(ctx.channel());
    }

    public void sendReqByOkHttp(final Channel uaChannel,
                                ProxyIp proxyIp,
                                ApnHandlerParams apnHandlerParams,
                                FullHttpRequest fullHttpRequest,
                                String handler) throws IOException {
        // get headers
        Map<String, String> headers = getReqHeaders(fullHttpRequest);

        // get uri params
        String method = fullHttpRequest.method().name();
        String uri = fullHttpRequest.uri();
        String remoteHost = proxyIp.getHost();
        int remotePort = proxyIp.getPort();
        String proxyUserName = proxyIp.getUserName();
        String proxyPassword = proxyIp.getPassword();

        // send request
        if (!"connect".equalsIgnoreCase(method)) {
            Response response = null;
            try {
                response = sendCommonReq(method, uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, fullHttpRequest, handler);
                // parse okhttp response and send netty response
                constructResponseAndSend(uaChannel, response, apnHandlerParams.getRequestMonitor(), method, uri);
            } finally {
                // 释放资源
                OkHttpTool.closeResponse(response);
                SocksServerUtils.closeOnFlush(uaChannel);
            }
        } else if ("connect".equalsIgnoreCase(method)) {
            sendConnectReqByOkhttp(uaChannel, proxyIp, apnHandlerParams, fullHttpRequest, handler);
        } else {
            throw new RuntimeException("不认识的请求方式: " + method);
        }
    }

    void sendConnectReqByOkhttp(final Channel uaChannel,
                                ProxyIp proxyIp,
                                ApnHandlerParams apnHandlerParams,
                                FullHttpRequest fullHttpRequest,
                                String handler) {
        // get headers
        Map<String, String> headers = getReqHeaders(fullHttpRequest);

        // get uri params
        String method = fullHttpRequest.method().name();
        String uri = fullHttpRequest.uri();
        String remoteHost = proxyIp.getHost();
        int remotePort = proxyIp.getPort();
        String proxyUserName = proxyIp.getUserName();
        String proxyPassword = proxyIp.getPassword();

        logger.error("okhttp 遇到了 connect 请求");
        // add ssl
        String keyStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\tunnel-server.jks";
        String trustStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\tunnel-server.cer";
        String password = "123456";
        SSLEngine engine = ApnProxySSLContextFactory.createServerSslEngine(keyStorePath, password, trustStorePath, password);
        uaChannel.pipeline().addFirst("apnproxy.encrypt", new SslHandler(Objects.requireNonNull(engine)));

        // handshake
        DefaultFullHttpResponse connectEstablished = buildConnectionResp();
        uaChannel.writeAndFlush(connectEstablished)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        // 使用其他的handler接收真实请求
                        uaChannel.pipeline().remove("idlestate");
                        uaChannel.pipeline().remove("idlehandler");
                        uaChannel.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_CODEC_NAME);
                        uaChannel.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_DECOMPRESSOR_NAME);
                        uaChannel.pipeline().remove(ConcurrentLimitHandler.HANDLER_NAME);
                        uaChannel.pipeline().remove(ApnProxySchemaHandler.HANDLER_NAME);
                        uaChannel.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
                        uaChannel.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

                        future.channel()
                                .pipeline()
                                .addLast(new SimpleChannelInboundHandler<Object>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        logger.debug("okhttp channelRead0");
                                        // get uri params
                                        if (msg instanceof DefaultFullHttpRequest) {
                                            DefaultFullHttpRequest request = (DefaultFullHttpRequest) msg;
                                            String url = request.uri();
                                            String method = request.method().name();
                                            Map<String, String> reqHeaders = getReqHeaders(fullHttpRequest);

                                            List<HttpContent> httpContents = new LinkedList<>();
                                            HttpContent content = (HttpContent) msg;
                                            httpContents.add(content);
                                            // send real request
                                            Response response = sendCommonReq(method, url, remoteHost, remotePort,
                                                    proxyUserName, proxyPassword, reqHeaders, fullHttpRequest, handler);
                                            constructResponseAndSend(uaChannel, response, apnHandlerParams.getRequestMonitor(), "connect", "");

                                            // close
                                            httpContents.clear();
                                            OkHttpTool.closeResponse(response);
                                        }
                                    }
                                });

                    } else {
                        String errorMsg = "send connection established fail";
                        uaChannel.writeAndFlush(errorMsg);
                        SocksServerUtils.closeOnFlush(uaChannel);

                        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), "okhttp send connection established fail", errorMsg);
                        IpMonitorUtils.error(apnHandlerParams.getRequestMonitor(), "okhttp send connection established fail", errorMsg);
                    }
                });
    }

    Map<String, String> getReqHeaders(HttpRequest httpRequest) {
        Set<String> headerNames = httpRequest.headers().names();
        HashMap<String, String> headers = new HashMap<>(headerNames.size());
        for (String headerName : headerNames) {
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Authorization")) {
                continue;
            }
            // 解决response乱码
            if (StringUtils.equalsIgnoreCase(headerName, "Accept-Encoding")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
                continue;
            }
            headers.put(headerName, httpRequest.headers().getAll(headerName).get(0));
        }
        headers.put(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        headers.put("Proxy-Connection", HttpHeaders.Values.CLOSE);
        return headers;
    }

    Response sendCommonReq(String method, String uri, String remoteHost, int remotePort, String proxyUserName,
                           String proxyPassword, Map<String, String> headers, FullHttpRequest fullHttpRequest, String handler) throws IOException {
        Map<String, String> reqHeaders = setConnection(headers);
        Response response;
        if ("get".equalsIgnoreCase(method)) {
            if (uri.startsWith("https")) {
                logger.warn("okhttp 出现 https, method=get");
                response = okHttpTool.sendGetByProxyWithSsl(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, null);
            } else {
                response = okHttpTool.sendGetByProxy(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, null);
            }
        } else if ("post".equalsIgnoreCase(method)) {
            // parse body
            byte[] body = getContent(handler, fullHttpRequest);
            if (uri.startsWith("https")) {
                logger.warn("okhttp 出现 https, method=post");
                response = okHttpTool.sendPostByProxyWithSsl(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, body);
            } else {
                response = okHttpTool.sendPostByProxy(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, body);
            }
        } else {
            throw new RuntimeException("不认识的请求方式: " + method);
        }
        return response;
    }

    Map<String, String> setConnection(Map<String, String> headers) {
        Map<String, String> reqHeader = new HashMap<>();
        headers.forEach((k, v) -> {
            if (!"connection".equalsIgnoreCase(k) && !"proxy-connection".equalsIgnoreCase(k)) {
                reqHeader.put(k, v);
            }
        });
        // 补充：只使用短连接
        reqHeader.put("Connection", "close");
        return reqHeader;
    }

    byte[] getContent(String handler, FullHttpRequest fullHttpRequest) {
        int len = fullHttpRequest.content().readableBytes();
        if (len > 0) {
            byte[] content = new byte[len];
            fullHttpRequest.content().readBytes(content);
            String contentStr = new String(content, StandardCharsets.UTF_8);
            logger.debug("{} 接收请求, 请求体: {}", handler, contentStr);
            return content;
        }
        return null;
    }

    void constructResponseAndSend(Channel uaChannel, Response response, RequestMonitor requestMonitor, String method, String uri) throws IOException {
        int contentLength = 0;
        // 收集请求头
        Headers headers = response.headers();
        Map<String, String> headerCollect = new HashMap<>(headers.size());
        for (String key : headers.names()) {
            String value = headers.get(key);
            headerCollect.put(key, value);
            if ("content-length".equalsIgnoreCase(key)) {
                contentLength = Integer.parseInt(value);
            }
        }

        // 设置请求统计信息, 注意：response.body().bytes() 被调用完，就会close
        byte[] bytes = Objects.requireNonNull(response.body()).bytes();
        requestMonitor.setSuccess(response.code() == HttpResponseStatus.OK.code());
        requestMonitor.setCode(response.code());

        // 响应体大小
        if (contentLength == 0) {
            contentLength = bytes.length;
            logger.debug("contentLength等于0, 使用字节数组的方式");
        }

        // 响应头大小
        StringBuilder builder = new StringBuilder();
        headerCollect.forEach((k, v) -> builder.append(k).append(": ").append(v).append(System.lineSeparator()));
        int headerSize = builder.toString().getBytes().length;

        // 响应报文大小
        int respSize = headerSize + contentLength;
        requestMonitor.getReponseSize().addAndGet(respSize);
        logger.debug("okhttp响应报文大小={}, header={}, body={}",
                SocksServerUtils.formatByte(respSize), SocksServerUtils.formatByte(headerSize), SocksServerUtils.formatByte(contentLength));

        // 处理响应
        HttpResponseStatus httpResponseStatus;
        int code = response.code();
        if (code == HttpResponseStatus.OK.code()) {
            httpResponseStatus = HttpResponseStatus.OK;
            // 监控
            ReqMonitorUtils.ok(requestMonitor, "OK_HTTP_TOOL");
            IpMonitorUtils.ok(requestMonitor, "OK_HTTP_TOOL");
        } else {
            httpResponseStatus = HttpResponseStatus.valueOf(code);
            if (httpResponseStatus == null) {
                httpResponseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            }
            String msg = new StringBuilder().append("okHttp not 200, proxyAddr=")
                    .append(requestMonitor.getProxyAddr())
                    .append(", method=").append(method)
                    .append(", code=").append(code)
                    .append(", reason=").append(Arrays.toString(bytes)).toString();
            logger.error(msg);

            // 监控
            ReqMonitorUtils.error(requestMonitor, "OK_HTTP_TOOL", msg);
            IpMonitorUtils.error(requestMonitor, "OK_HTTP_TOOL", msg);
        }

        // 模拟 netty 响应
        ByteBuf responseContent = Unpooled.copiedBuffer(bytes);
        DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, responseContent);
        headerCollect.forEach((key, value) -> fullHttpResponse.headers().add(key, value));
        uaChannel.writeAndFlush(fullHttpResponse);
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

    /**
     * 转发 connect 请求
     *
     * @param requestMonitor 请求监控工具
     * @param ctx            处理器上下文
     * @param httpRequest    请求
     * @param proxyIp        代理ip
     * @param tunnelInstance 隧道实例
     */
    public void sendConnectReqByNettyClient(RequestMonitor requestMonitor,
                                            final ChannelHandlerContext ctx,
                                            FullHttpRequest httpRequest,
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
//                // fixed failed to allocate 2048 byte(s) of direct memory
//                .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .handler(new TunnelRelayChannelInitializer(requestMonitor, proxyIp, uaChannel, tunnelInstance))
                .connect(proxyIp.getHost(), proxyIp.getPort())
                .addListener((ChannelFutureListener) future1 -> {
                    long took = System.currentTimeMillis() - begin;
                    if (future1.isSuccess()) {
                        logger.debug("tunnel_handler 连接代理IP [{}] 成功，耗时: {} ms", proxyIp.getRemote(), took);
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

                        logger.debug("tunnel_handler 重新构造请求之前：{}{}", System.lineSeparator(), httpRequest);
                        String newConnectRequest = constructReqForConnect(httpRequest, proxyIp);
                        //String newConnectRequest = buildReqForConnect(httpRequest, apnProxyRemote);
                        logger.debug("tunnel_handler 重新构造请求之后：{}{}", System.lineSeparator(), newConnectRequest);
                        logger.debug("tunnel_handler 重新构造请求之后：size={}", newConnectRequest.getBytes().length);

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
                    } else {
                        String errorMessage = future1.cause().getMessage();
                        logger.error("tunnel_handler 连接代理IP失败，耗时: {} ms, reason={}", took, errorMessage);

                        // 监控统计
                        ReqMonitorUtils.error(requestMonitor, "sendTunnelReq", errorMessage);
                        IpMonitorUtils.error(requestMonitor, "sendTunnelReq", errorMessage);

                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(new DefaultHttpResponse(httpRequest.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR));
//                        ChannelPromise channelPromise = ctx.channel().newPromise();
//                        channelPromise.setFailure(new IOException("connect ip fail"));

                        // 关闭资源
                        ctx.channel().close();
                        future1.channel().close();
//                        SocksServerUtils.closeOnFlush(ctx.channel());
//                        SocksServerUtils.closeOnFlush(future1.channel());
                    }

                });
    }

    void removeHandler(String name, ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (pipeline.names().contains(name)) {
            pipeline.remove(name);
        }
    }

}
