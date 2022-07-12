package com.dataeye.proxy.apn.service;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.handler.*;
import com.dataeye.proxy.apn.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.apn.initializer.DirectRelayChannelInitializer;
import com.dataeye.proxy.apn.initializer.TunnelRelayChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.utils.ApnProxySSLContextFactory;
import com.dataeye.proxy.apn.utils.HttpErrorUtils;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.IpPoolScheduleService;
import com.dataeye.proxy.utils.IpMonitorUtils;
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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/7 12:19
 * @description 请求分发
 */
@Service
public class RequestDistributeService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    @Resource
    IpPoolScheduleService ipPoolScheduleService;

    public HttpRequest constructReqForCommon(HttpRequest httpRequest,
                                             ApnProxyRemote apnProxyRemote) {

        String uri = httpRequest.getUri();

        if (!apnProxyRemote.isAppleyRemoteRule()) {
            uri = this.getPartialUrl(uri);
        }

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

        if (StringUtils.isNotBlank(apnProxyRemote.getProxyUserName())
                && StringUtils.isNotBlank(apnProxyRemote.getProxyPassword())) {
            _httpRequest.headers().set("Proxy-Authorization", Credentials.basic(apnProxyRemote.getProxyUserName(), apnProxyRemote.getProxyPassword()));
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
                                         ApnProxyRemote apnProxyRemote) {
        String CRLF = "\r\n";
        String url = httpRequest.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append(httpRequest.getMethod().name()).append(" ").append(url).append(" ")
                .append(httpRequest.getProtocolVersion().text()).append(CRLF);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
//            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
//                continue;
//            }
//            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
//                continue;
//            }
//            // 临时增加
//            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Authorization")) {
//                continue;
//            }
            for (String headerValue : httpRequest.headers().getAll(headerName)) {
                sb.append(headerName).append(": ").append(headerValue).append(CRLF);
            }
        }

        if (StringUtils.isNotBlank(apnProxyRemote.getProxyUserName())
                && StringUtils.isNotBlank(apnProxyRemote.getProxyPassword())) {
            sb.append("Proxy-Authorization: " + Credentials.basic(apnProxyRemote.getProxyUserName(), apnProxyRemote.getProxyPassword())).append(CRLF);
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

    public void sendReqByOkHttp(final Channel uaChannel,
                                ApnProxyRemote apnProxyRemote,
                                ApnHandlerParams apnHandlerParams,
                                FullHttpRequest fullHttpRequest,
                                String handler) throws IOException {
        // get headers
        Map<String, String> headers = getReqHeaders(fullHttpRequest);

        // get uri params
        String method = fullHttpRequest.method().name();
        String uri = fullHttpRequest.uri();
        String remoteHost = apnProxyRemote.getRemoteHost();
        int remotePort = apnProxyRemote.getRemotePort();
        String proxyUserName = apnProxyRemote.getProxyUserName();
        String proxyPassword = apnProxyRemote.getProxyPassword();

        // send request
        if (!"connect".equalsIgnoreCase(method)) {
            Response response = sendCommonReq(method, uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, fullHttpRequest, handler);
            // parse okhttp response and send netty response
            constructResponseAndSend(uaChannel, response, apnHandlerParams.getRequestMonitor());
        } else if ("connect".equalsIgnoreCase(method)) {
            // add ssl
            // todo 待测试,暂时没有调通
            String keyStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\tunnel-server.jks";
            String trustStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\tunnel-server.cer";
            String password = "123456";
            SSLEngine engine = ApnProxySSLContextFactory.createSslEngine(keyStorePath, password, trustStorePath, password);
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
                                                constructResponseAndSend(uaChannel, response, apnHandlerParams.getRequestMonitor());

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

        } else {
            throw new RuntimeException("不认识的请求方式: " + method);
        }
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
        Response response;
        if ("get".equalsIgnoreCase(method)) {
            if (uri.startsWith("https")) {
                response = OkHttpTool.sendGetByProxyWithSsl(uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, null);
            } else {
                response = OkHttpTool.sendGetByProxy(uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, null);
            }
        } else if ("post".equalsIgnoreCase(method)) {
            // parse body
            byte[] body = getContent(handler, fullHttpRequest);
            if (uri.startsWith("https")) {
                response = OkHttpTool.sendPostByProxyWithSsl(uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, body);
            } else {
                response = OkHttpTool.sendPostByProxy(uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, body);
            }
        } else {
            throw new RuntimeException("不认识的请求方式: " + method);
        }
        return response;
    }

    byte[] getContent(String handler, FullHttpRequest fullHttpRequest) {
        int len = fullHttpRequest.content().readableBytes();
        if (len > 0) {
            byte[] content = new byte[len];
            fullHttpRequest.content().readBytes(content);
            String contentStr = new String(content, StandardCharsets.UTF_8);
            logger.info("{} 接收请求, 请求体: {}", handler, contentStr);
            return content;
        }
        return null;
    }

    void constructResponseAndSend(Channel uaChannel, Response response, RequestMonitor requestMonitor) throws IOException {
        // headers
        Headers headers = response.headers();
        Map<String, String> headerCollect = new HashMap<>(headers.size());
        for (String key : headers.names()) {
            String value = headers.get(key);
            headerCollect.put(key, value);
        }
        headerCollect.put(HttpHeaders.Names.CONNECTION, "close");

        // handle reponse
        int code = response.code();
        if (code == HttpResponseStatus.OK.code()) {
            // 注意：response.body().bytes() 被调用完，就会close
            byte[] result = Objects.requireNonNull(response.body()).bytes();
            //System.out.println("响应=====" + new String(result, StandardCharsets.UTF_8) + ", result====" + result.length);
            requestMonitor.getReponseSize().addAndGet(result.length);

            // collect headers and construct netty response
            ByteBuf responseContent = Unpooled.copiedBuffer(result);
            DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseContent);
            headerCollect.forEach((key, value) -> fullHttpResponse.headers().add(key, value));
            //System.out.println("ok responseContent refCnt=" + responseContent.refCnt());
            //System.out.println("ok fullHttpResponse refCnt=" + fullHttpResponse.refCnt());
            uaChannel.writeAndFlush(fullHttpResponse);

            // 监控
            ReqMonitorUtils.ok(requestMonitor, "OK_HTTP_TOOL");
            IpMonitorUtils.ok(requestMonitor, "OK_HTTP_TOOL");
            //System.out.println("ok responseContent2 refCnt=" + responseContent.refCnt());
            //System.out.println("ok fullHttpResponse2 refCnt=" + fullHttpResponse.refCnt());
        } else {
            HttpResponseStatus httpResponseStatus = HttpResponseStatus.valueOf(code);
            if (httpResponseStatus == null) {
                httpResponseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            }
            String errMsg = Objects.requireNonNull(response.body()).string();
            String msg = "ok http send fail, code=" + code + ", reason=" + errMsg;
            logger.error(msg);

            // 模拟 netty 响应
            ByteBuf responseContent = Unpooled.copiedBuffer(errMsg, CharsetUtil.UTF_8);
            DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, responseContent);
            headerCollect.forEach((key, value) -> fullHttpResponse.headers().add(key, value));
            //System.out.println("error responseContent refCnt=" + responseContent.refCnt());
            //System.out.println("error fullHttpResponse refCnt=" + fullHttpResponse.refCnt());
            uaChannel.writeAndFlush(fullHttpResponse);

            // 监控
            ReqMonitorUtils.error(requestMonitor, "OK_HTTP_TOOL", msg);
            IpMonitorUtils.error(requestMonitor, "OK_HTTP_TOOL", msg);
            //System.out.println("error responseContent2 refCnt=" + responseContent.refCnt());
            //System.out.println("error fullHttpResponse2 refCnt=" + fullHttpResponse.refCnt());
        }
        // 释放资源
        OkHttpTool.closeResponse(response);
        SocksServerUtils.closeOnFlush(uaChannel);
    }

    DefaultFullHttpResponse buildConnectionResp() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                new HttpResponseStatus(200, "Connection established"));
    }

    /**
     * 转发普通请求
     *
     * @param uaChannel         源通道
     * @param apnHandlerParams  handler相关参数
     * @param apnProxyRemote    代理ip
     * @param tunnelInstance    隧道实例
     * @param httpContentBuffer http content缓存
     * @param msg               传递的源数据
     */
    public void forwardCommonReq(final Channel uaChannel,
                                 ApnHandlerParams apnHandlerParams,
                                 ApnProxyRemote apnProxyRemote,
                                 TunnelInstance tunnelInstance,
                                 List<HttpContent> httpContentBuffer,
                                 Object msg) {
        long begin = System.currentTimeMillis();
        String remoteAddr = apnProxyRemote.getRemote();
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        DirectRelayHandler.RemoteChannelInactiveCallback cb = (remoteChannelCtx, inactiveRemoteAddr) -> {
            logger.debug("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
            uaChannel.close();
        };

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
//                // 这个参数设定的是HTTP连接成功后，等待读取数据或者写数据的最大超时时间，单位为毫秒
//                // 如果设置为0，则表示永远不会超时
//                .option(ChannelOption.SO_TIMEOUT, tunnelInstance.getConnectTimeoutMillis())
//                // fixed failed to allocate 2048 byte(s) of direct memory
//                .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .handler(new DirectRelayChannelInitializer(apnHandlerParams, apnProxyRemote, uaChannel, remoteAddr, cb))
                .connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort())
                .addListener((ChannelFutureListener) future -> {
                    long took = System.currentTimeMillis() - begin;
                    // 执行连接后操作，可能连接成功，也可能失败
                    if (future.isSuccess()) {
                        logger.debug("forward_handler 连接代理IP成功, ip={}, 耗时={} ms", apnProxyRemote.getRemote(), took);
                        HttpRequest oldRequest = (HttpRequest) msg;
                        logger.debug("forward_handler 构造请求之前：{}", oldRequest);
                        HttpRequest newRequest = constructReqForCommon(oldRequest, apnProxyRemote);
                        logger.debug("forward_handler 构造请求之后：{}", newRequest);
                        future.channel().write(newRequest);

                        logger.debug("httpContentBuffer size={}", httpContentBuffer.size());
                        for (HttpContent hc : httpContentBuffer) {
                            future.channel().writeAndFlush(hc);
                        }
                        httpContentBuffer.clear();

                        // EMPTY_BUFFER 标识数据已经写完,会自动关闭
                        future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                .addListener((ChannelFutureListener) future1 -> future1.channel().read());
                    } else {
                        // todo 如果失败，需要在这里使用新的ip重试（后续改造）
                        String errorMsg;
                        ConcurrentLinkedQueue<ProxyIp> proxyCfgs = ipPoolScheduleService.getProxyIpPool().get(tunnelInstance.getAlias());
                        if (proxyCfgs == null || proxyCfgs.isEmpty()) {
                            errorMsg = "forward_handler连接代理IP失败, ip=" + remoteAddr + ", 具体原因: ip池为空";
                        } else {
                            List<String> ipList = proxyCfgs.stream().filter(Objects::nonNull)
                                    .map(item -> item.getHost() + "(" + item.getExpireTime() + ")")
                                    .collect(Collectors.toList());
                            String errorMessage = future.cause().getMessage();
                            errorMsg = "forward_handler连接代理IP失败, ip=" + remoteAddr + " , 耗时：" + took + " ms, " +
                                    "具体原因: " + errorMessage + ", 此时的ip池列表：" + ipList.toString();
                        }

                        // send error response
                        logger.error(errorMsg);
                        HttpMessage errorResponseMsg = HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorMsg);
                        uaChannel.writeAndFlush(errorResponseMsg);

                        // 统计
                        ReqMonitorUtils.error(requestMonitor, "sendForwardReq", errorMsg);
                        IpMonitorUtils.error(requestMonitor, "sendForwardReq", errorMsg);

                        // 关闭资源
                        SocksServerUtils.closeOnFlush(uaChannel);
                        httpContentBuffer.clear();
                        future.channel().close();
                    }
                });
    }

    /**
     * 转发 connect 请求
     *
     * @param requestMonitor 请求监控工具
     * @param ctx            处理器上下文
     * @param httpRequest    请求
     * @param apnProxyRemote 代理ip
     * @param tunnelInstance 隧道实例
     */
    public void forwardConnectReq(RequestMonitor requestMonitor,
                                  final ChannelHandlerContext ctx,
                                  FullHttpRequest httpRequest,
                                  ApnProxyRemote apnProxyRemote,
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
                .handler(new TunnelRelayChannelInitializer(requestMonitor, apnProxyRemote, uaChannel))
                .connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort())
                .addListener((ChannelFutureListener) future1 -> {
                    long took = System.currentTimeMillis() - begin;
                    if (future1.isSuccess()) {
                        logger.info("tunnel_handler 连接代理IP [{}] 成功，耗时: {} ms", apnProxyRemote.getRemote(), took);
                        if (apnProxyRemote.isAppleyRemoteRule()) {
                            // remove之前
                            //System.out.println("tunnel_handler remove之前=" + ctx.pipeline().toMap().size());
                            //ctx.pipeline().toMap().keySet().forEach(System.out::println);

                            // todo console总是会报没有这个handler
//                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_IDLE_STATE_NAME);
                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_IDLE_HANDLER_NAME);
                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_CODEC_NAME);
                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_AGG_NAME);
                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_DECOMPRESSOR_NAME);
//                            ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_BANDWIDTH_MONITOR_NAME);
                            ctx.pipeline().remove(ConcurrentLimitHandler.HANDLER_NAME);
                            ctx.pipeline().remove(ApnProxySchemaHandler.HANDLER_NAME);
                            ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);

                            // remove之后
                            //System.out.println("tunnel_handler remove之后=" + ctx.pipeline().toMap().size());
                            //ctx.pipeline().toMap().keySet().forEach(System.out::println);
                            ctx.pipeline().addLast(new TunnelRelayHandler(requestMonitor, "UA --> " + apnProxyRemote.getIpAddr(), future1.channel()));

                            logger.info("tunnel_handler 重新构造请求之前：{}", httpRequest);
                            String newConnectRequest = constructReqForConnect(httpRequest, apnProxyRemote);
                            logger.info("tunnel_handler 重新构造请求之后：{}", newConnectRequest);

                            ByteBuf reqContent = Unpooled.copiedBuffer(newConnectRequest, CharsetUtil.UTF_8);
                            // ReferenceCountUtil.releaseLater() will keep the reference of buf,
                            // and then release it when the test thread is terminated.
                            // ReferenceCountUtil.releaseLater(reqContent);
                            //System.out.println("out: httpRequest refCnt=" + httpRequest.refCnt() + ", reqContent refCnt=" + reqContent.refCnt());
                            future1.channel()
                                    .writeAndFlush(reqContent)
                                    .addListener((ChannelFutureListener) future2 -> {
                                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                                            future2.channel().read();
                                        }
                                        //System.out.println("addListener: httpRequest refCnt=" + httpRequest.refCnt() + ", reqContent refCnt=" + reqContent.refCnt());
                                    });
                        } else {
                            logger.debug("tunnel_handler 使用本地ip转发");
                            // send connect success msg to UA
                            HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    new HttpResponseStatus(200, "Connection established"));
                            ctx.writeAndFlush(proxyConnectSuccessResponse)
                                    .addListener((ChannelFutureListener) future2 -> {
                                        // remove handlers
                                        ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_CODEC_NAME);
                                        ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_AGG_NAME);
                                        ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_DECOMPRESSOR_NAME);
                                        //ctx.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_BANDWIDTH_MONITOR_NAME);
                                        ctx.pipeline().remove(ConcurrentLimitHandler.HANDLER_NAME);
                                        ctx.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);
                                        // add relay handler
                                        ctx.pipeline().addLast(new TunnelRelayHandler(requestMonitor, "UA --> " + apnProxyRemote.getIpAddr(), future1.channel()));
                                    });
                        }
                    } else {
                        String errorMessage = future1.cause().getMessage();
                        logger.error("tunnel_handler 连接代理IP失败，耗时: {} ms, reason={}", took, errorMessage);

                        // 监控统计
                        ReqMonitorUtils.error(requestMonitor, "sendTunnelReq",errorMessage);
                        IpMonitorUtils.error(requestMonitor, "sendTunnelReq", errorMessage);

                        // 关闭资源
                        SocksServerUtils.closeOnFlush(ctx.channel());
                        SocksServerUtils.closeOnFlush(future1.channel());
                    }

                });
    }

    /**
     * 普通请求转发任务
     */
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
            forwardCommonReq(uaChannel, apnHandlerParams, apnProxyRemote, tunnelInstance, httpContentBuffer, msg);
        }
    }

    /**
     * connect请求转发任务
     */
    class TunnelRequestTask implements Runnable {

        private final ChannelHandlerContext ctx;
        private final FullHttpRequest httpRequest;
        private final ApnProxyRemote apnProxyRemote;
        private final TunnelInstance tunnelInstance;
        private final RequestMonitor requestMonitor;

        public TunnelRequestTask(RequestMonitor requestMonitor,
                                 final ChannelHandlerContext ctx,
                                 FullHttpRequest httpRequest,
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
            forwardConnectReq(requestMonitor, ctx, httpRequest, apnProxyRemote, tunnelInstance);
        }
    }

}
