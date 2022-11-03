package com.dataeye.proxy.server.forward.impl;

import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.cons.HttpCons;
import com.dataeye.proxy.cons.Log;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.server.forward.RequestForwardService;
import com.dataeye.proxy.utils.OkHttpTool;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import okhttp3.Headers;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author jaret
 * @date 2022/11/2 10:58
 * @description
 */
@Service
public class OkHttpForwardServiceImpl implements RequestForwardService {

    @Autowired
    OkHttpTool okHttpTool;

    @Override
    public void sendHttp(ChannelHandlerContext ctx,
                         ProxyIp proxyIp,
                         ApnHandlerParams apnHandlerParams,
                         FullHttpRequest fullHttpRequest,
                         String handler) {
        String method = fullHttpRequest.method().name();
        if (!HttpCons.GET.equalsIgnoreCase(method) && !HttpCons.POST.equalsIgnoreCase(method)) {
            return;
        }

        // headers
        Map<String, String> headers = getReqHeaders(fullHttpRequest);

        // proxy
        String uri = fullHttpRequest.uri();
        String remoteHost = proxyIp.getHost();
        int remotePort = proxyIp.getPort();
        String proxyUserName = proxyIp.getUserName();
        String proxyPassword = proxyIp.getPassword();

        Channel uaChannel = ctx.channel();
        Response response = null;
        try {
            response = sendReq(Log.SERVER, method, uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, fullHttpRequest, handler);
            // parse okhttp response and return netty response
            constructNettyResponseAndSend(Log.SERVER, uaChannel, response, apnHandlerParams.getRequestMonitor(), method, uri);
        } catch (Exception e) {
            Log.SERVER.info("okhttp 转发 http 异常: {}", e.getMessage(), e);
            handleErrorResp(ctx, e.getMessage());
        } finally {
            // 释放资源
            OkHttpTool.closeResponse(response);
            SocksServerUtils.closeOnFlush(uaChannel);
        }
    }

    @Override
    public void sendhttps(ChannelHandlerContext ctx,
                          ProxyIp proxyIp,
                          ApnHandlerParams apnHandlerParams,
                          FullHttpRequest fullHttpRequest,
                          String handler) {
        String method = fullHttpRequest.method().name();
        if (!HttpCons.CONNECT.equalsIgnoreCase(method)) {
            return;
        }
        // headers
        Map<String, String> headers = getReqHeaders(fullHttpRequest);

        // proxy
        String uri = fullHttpRequest.uri();
        String remoteHost = proxyIp.getHost();
        int remotePort = proxyIp.getPort();
        String proxyUserName = proxyIp.getUserName();
        String proxyPassword = proxyIp.getPassword();

        // send request
        Channel uaChannel = ctx.channel();
        Response response = null;
        try {
            response = sendReqWithSsl(Log.SERVER, method, uri, remoteHost, remotePort, proxyUserName, proxyPassword, headers, fullHttpRequest, handler);
            // parse okhttp response and return netty response
            constructNettyResponseAndSend(Log.SERVER, uaChannel, response, apnHandlerParams.getRequestMonitor(), method, uri);
//            sendConnectReqByOkhttp(uaChannel, proxyIp, apnHandlerParams, fullHttpRequest, handler);
        } catch (IOException e) {
            Log.SERVER.info("okhttp 转发 https 异常: {}", e.getMessage(), e);
            handleErrorResp(ctx, e.getMessage());
        } finally {
            // 释放资源
            OkHttpTool.closeResponse(response);
            SocksServerUtils.closeOnFlush(uaChannel);
        }
    }

    @Override
    public void sendSocks5(ChannelHandlerContext ctx, ProxyIp proxyIp, ApnHandlerParams apnHandlerParams, FullHttpRequest fullHttpRequest, String handler) {

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

    void handleErrorResp(ChannelHandlerContext ctx, String errorMsg) {
        ByteBuf content = Unpooled.copiedBuffer(errorMsg, CharsetUtil.UTF_8);
        FullHttpMessage errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
        ctx.channel().writeAndFlush(errorResponse);
        SocksServerUtils.closeOnFlush(ctx.channel());
    }

    Response sendReq(Logger logger, String method, String uri, String remoteHost, int remotePort, String proxyUserName,
                           String proxyPassword, Map<String, String> headers, FullHttpRequest fullHttpRequest, String handler) throws IOException {
        Map<String, String> reqHeaders = setConnection(headers);
        Response response;
        if (HttpCons.GET.equalsIgnoreCase(method)) {
            response = okHttpTool.sendGetByProxy(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, null);
        } else if (HttpCons.POST.equalsIgnoreCase(method)) {
            // parse body
            byte[] body = getContent(logger, handler, fullHttpRequest);
            response = okHttpTool.sendPostByProxy(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, body);
        } else {
            throw new RuntimeException("unknown request method: " + method);
        }
        return response;
    }

    Response sendReqWithSsl(Logger logger, String method, String uri, String remoteHost, int remotePort, String proxyUserName,
                           String proxyPassword, Map<String, String> headers, FullHttpRequest fullHttpRequest, String handler) throws IOException {
        Map<String, String> reqHeaders = setConnection(headers);
        Response response;
        if (HttpCons.GET.equalsIgnoreCase(method)) {
            response = okHttpTool.sendGetByProxyWithSsl(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, null);
        } else if (HttpCons.POST.equalsIgnoreCase(method)) {
            // parse body
            byte[] body = getContent(logger, handler, fullHttpRequest);
            response = okHttpTool.sendPostByProxyWithSsl(uri, remoteHost, remotePort, proxyUserName, proxyPassword, reqHeaders, body);
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

    byte[] getContent(Logger logger, String handler, FullHttpRequest fullHttpRequest) {
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

    void constructNettyResponseAndSend(Logger logger, Channel uaChannel, Response response, RequestMonitor requestMonitor, String method, String uri) throws IOException {
        int contentLength = 0;
        // 收集请求头
        Headers headers = response.headers();
        Map<String, String> headerCollect = new HashMap<>(headers.size());
        for (String key : headers.names()) {
            String value = headers.get(key);
            headerCollect.put(key, value);
            if (StringUtils.isNotBlank(value) && org.apache.http.HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) {
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

//    void sendConnectReqByOkhttp(final Channel uaChannel,
//                                ProxyIp proxyIp,
//                                ApnHandlerParams apnHandlerParams,
//                                FullHttpRequest fullHttpRequest,
//                                String handler) {
//        // get headers
//        Map<String, String> headers = getReqHeaders(fullHttpRequest);
//
//        // get uri params
//        String method = fullHttpRequest.method().name();
//        String uri = fullHttpRequest.uri();
//        String remoteHost = proxyIp.getHost();
//        int remotePort = proxyIp.getPort();
//        String proxyUserName = proxyIp.getUserName();
//        String proxyPassword = proxyIp.getPassword();
//
//        logger.error("okhttp 遇到了 connect 请求");
//        // add ssl
//        String keyStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\tunnel-server.jks";
//        String trustStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\tunnel-server.cer";
//        String password = "123456";
//        SSLEngine engine = ApnProxySSLContextFactory.createSslEngine(keyStorePath, password, trustStorePath, password);
//        uaChannel.pipeline().addFirst("apnproxy.encrypt", new SslHandler(Objects.requireNonNull(engine)));
//
//        // handshake
//        DefaultFullHttpResponse connectEstablished = buildConnectionResp();
//        uaChannel.writeAndFlush(connectEstablished)
//                .addListener((ChannelFutureListener) future -> {
//                    if (future.isSuccess()) {
//                        // 使用其他的handler接收真实请求
//                        uaChannel.pipeline().remove("idlestate");
//                        uaChannel.pipeline().remove("idlehandler");
//                        uaChannel.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_CODEC_NAME);
//                        uaChannel.pipeline().remove(ApnProxyServerChannelInitializer.SERVER_REQUEST_DECOMPRESSOR_NAME);
//                        uaChannel.pipeline().remove(ConcurrentLimitHandler.HANDLER_NAME);
//                        uaChannel.pipeline().remove(ApnProxySchemaHandler.HANDLER_NAME);
//                        uaChannel.pipeline().remove(ApnProxyForwardHandler.HANDLER_NAME);
//                        uaChannel.pipeline().remove(ApnProxyTunnelHandler.HANDLER_NAME);
//
//                        future.channel()
//                                .pipeline()
//                                .addLast(new SimpleChannelInboundHandler<Object>() {
//                                    @Override
//                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
//                                        logger.debug("okhttp channelRead0");
//                                        // get uri params
//                                        if (msg instanceof DefaultFullHttpRequest) {
//                                            DefaultFullHttpRequest request = (DefaultFullHttpRequest) msg;
//                                            String url = request.uri();
//                                            String method = request.method().name();
//                                            Map<String, String> reqHeaders = getReqHeaders(fullHttpRequest);
//
//                                            List<HttpContent> httpContents = new LinkedList<>();
//                                            HttpContent content = (HttpContent) msg;
//                                            httpContents.add(content);
//                                            // send real request
//                                            Response response = sendCommonReq(method, url, remoteHost, remotePort,
//                                                    proxyUserName, proxyPassword, reqHeaders, fullHttpRequest, handler);
//                                            constructResponseAndSend(uaChannel, response, apnHandlerParams.getRequestMonitor(), "connect", "");
//
//                                            // close
//                                            httpContents.clear();
//                                            OkHttpTool.closeResponse(response);
//                                        }
//                                    }
//                                });
//
//                    } else {
//                        String errorMsg = "send connection established fail";
//                        uaChannel.writeAndFlush(errorMsg);
//                        SocksServerUtils.closeOnFlush(uaChannel);
//
//                        ReqMonitorUtils.error(apnHandlerParams.getRequestMonitor(), "okhttp send connection established fail", errorMsg);
//                        IpMonitorUtils.error(apnHandlerParams.getRequestMonitor(), "okhttp send connection established fail", errorMsg);
//                    }
//                });
//    }

}
