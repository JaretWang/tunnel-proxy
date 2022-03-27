package com.dataeye.proxy.http.handler;

import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.utils.HostNamePortUtils;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.NoArgsConstructor;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

@Component
@Scope("prototype")
@NoArgsConstructor
public class ThreadForwardHandler implements ChannelInboundHandler {

    private static final Logger log = LoggerFactory.getLogger(ThreadForwardHandler.class);

    /**
     * 真实的目标地址
     */
    private String host;
    /**
     * 真正的目标地址端口
     */
    private int port;
    private ProxyServerConfig proxyServerConfig;
    private ThreadPoolTaskExecutor ioThreadPool;
    private static final boolean USE_TUNNEL_PROXY = true;
    private static final String RESPONSE_HTML = System.getProperty("user.dir") + File.separator + "proxy_result" + File.separator + "response.html";
    private static final HashMap<String, String> HEADERS = new HashMap<String, String>() {
        {
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36");
            put("Connection", "close");
        }
    };
//    private DefaultHttpRequest request;
    private HttpRequest request;
    private ArrayList<HttpContent> contents = new ArrayList<>();
    private ProxyRequestTask proxyRequestTask;

    public ThreadForwardHandler(ProxyServerConfig proxyServerConfig, ThreadPoolTaskExecutor ioThreadPool) {
        this.proxyServerConfig = proxyServerConfig;
        this.ioThreadPool = ioThreadPool;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            log.warn("循环读取, 消息类型：{}",msg.getClass());
            request = (HttpRequest) msg;
        }
        else {
            // SimpleChannelInboundHandler会将HttpContent中的bytebuf Release，但是这个还会转给relayHandler（中继转发），所以需要在这里预先retain(保持)
            // 把所有的 HttpContent 全部收集起来，放入集合里面
            ((HttpContent) msg).content().retain();
            contents.add((HttpContent) msg);
            // 最终这个msg的类型为 EmptyLastHttpContent，表示一个完整的 Http 请求被收到，开始处理该请求
            if (msg instanceof LastHttpContent) {
//                request = (DefaultHttpRequest) msg;
                // 获取真正的目标地址和端口
                log.warn("读取完毕，开始处理...");
                setRealHostPort(ctx);
                if (request.method().equals(HttpMethod.CONNECT)) {
                    log.info("接收 CONNECT 类型请求, 转发给代理商, 类型：{}, URI：{}, host: {}, port: {}", request.method(), request.uri(), host, port);

                    String proxyHost;
                    int proxyPort;
                    if (USE_TUNNEL_PROXY) {
                        buildProxyAuthorizationHeaderForTunnel(request);
                        proxyHost = proxyServerConfig.getTunnelProxyServerIp();
                        proxyPort = proxyServerConfig.getTunnelProxyServerPort();
                        String tunnelProxyUsername = proxyServerConfig.getTunnelProxyUsername();
                        String tunnelProxyPassword = proxyServerConfig.getTunnelProxyPassword();
                        String credential = Credentials.basic(tunnelProxyUsername, tunnelProxyPassword);
                        request.headers().set("Proxy-Authorization",credential);
                    } else {
                        // 直连代理ip
                        String proxyHostPort = IpSelector.randomGetProxyIp();
                        log.info("随机获取的代理ip内容：{}", proxyHostPort);
                        String[] split = proxyHostPort.split(":");
                        if (split.length != 2) {
                            throw new RuntimeException("代理ip格式有误");
                        }
                        proxyHost = split[0];
                        proxyPort = Integer.parseInt(split[1]);
                    }

                    proxyRequestTask = new ProxyRequestTask(ctx, proxyHost, proxyPort);
                    // 提交代理请求任务
                    ioThreadPool.submit(proxyRequestTask);
//                ReferenceCountUtil.release(msg);
                } else {
                    log.info("不支持其他类型请求, 类型：{}, URI：{}, host: {}, port: {}", request.method(), request.uri(), host, port);
                    DefaultHttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
                    // 响应写回通道
                    ctx.channel().writeAndFlush(response);
                    // 关闭通道
                    SocksServerUtils.closeOnFlush(ctx.channel());
                }
            } else {
//                if (msg instanceof LastHttpContent) {
//                    log.warn("请求内容接收完毕直接放行, 类型：{}", msg.getClass());
//                } else {
//                    log.warn("非 http 请求直接放行, 类型：{}", msg.getClass());
//                }

                log.warn("非 http 请求直接放行, 类型：{}", msg.getClass());
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        log.warn("处理完毕");
        ctx.close();

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {

    }

//    @Override
//    public void channelRead0(final ChannelHandlerContext ctx, HttpObject msg) {
//
//    }

    /**
     * 代理请求任务
     */
    class ProxyRequestTask implements Runnable {

        private final ChannelHandlerContext ctx;
        private final String proxyHost;
        private final int proxyPort;
        private final Bootstrap bootstrap = new Bootstrap();
        private final Channel originalChannel;


        public ProxyRequestTask(ChannelHandlerContext ctx, String proxyHost, int proxyPort) {
            this.ctx = ctx;
            this.originalChannel = ctx.channel();
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
        }

        @Override
        public void run() {
//            ctx.channel().config().setAutoRead(false);
            bootstrap.group(originalChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, false)
                    .handler(new LoggingHandler(LogLevel.DEBUG));
//                    .handler(new HttpClientCodec())
//                    .handler(new HttpObjectAggregator(HandlerCons.HTTP_OBJECT_AGGREGATOR_SIZE));
            bootstrap.connect(proxyHost, proxyPort).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // 连接成功后，直接转发响应给client
                    log.info("转发请求 {} 给代理商成功，并响应给client", request.uri());

                    Channel outboundChannel = future.channel();
//                    outboundChannel.pipeline().addLast(new HttpResponseDecoder());
//                    outboundChannel.pipeline().addLast(new HttpRequestEncoder());
                    outboundChannel.pipeline().addLast(new SecondForwardHandler(outboundChannel));
                    ctx.channel().writeAndFlush(outboundChannel);

//                    ctx.channel().writeAndFlush(
//                            new DefaultHttpResponse(request.protocolVersion(), new HttpResponseStatus(200, "Connection Established")));

//                    ChannelFuture responseFuture = ctx.channel().writeAndFlush(
//                            new DefaultHttpResponse(request.protocolVersion(), new HttpResponseStatus(200, "Connection Established")));

//                    responseFuture.addListener(new ChannelFutureListener() {
//                        @Override
//                        public void operationComplete(ChannelFuture channelFuture) {
//                            if (channelFuture.isSuccess()) {
//                                // 移除原本的处理器，因为第一次访问成功的消息回传
//                                ctx.pipeline().remove(HttpRequestDecoder.class);
//                                ctx.pipeline().remove(HttpResponseEncoder.class);
//                                ctx.pipeline().remove(HttpServerExpectContinueHandler.class);
//                                ctx.pipeline().remove(HttpProxyConnectHandler.class);
//                                // 添加新的处理器。为了去除代理请求头，重新构造新的header url ，再次发起类似 get post请求，而不是connect请求
////                                                    outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
//                                outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel(), relaySpan, proxyHostPort));
//                                ctx.pipeline().addLast(new RelayHandler(outboundChannel, relaySpan, proxyHostPort));
////                                                    ctx.channel().config().setAutoRead(true);
//                            } else {
//                                log.error("reply tunnel established Failed: " + ctx.channel().remoteAddress() + " " + request.method() + " " + request.uri());
//                                SocksServerUtils.closeOnFlush(ctx.channel());
//                                SocksServerUtils.closeOnFlush(outboundChannel);
//                            }
//                        }
//                    });

//                    Channel remoteChannel = future.channel();
//                    ctx.channel().writeAndFlush(responseFuture);
//                    remoteChannel.close();
//                    ReferenceCountUtil.release(msg);
//                    SocksServerUtils.closeOnFlush(remoteChannel);
                }

//                        final Channel outboundChannel = future.channel();
//                        if (future.isSuccess()) {
//                            // 第一次写一个成功响应。表示通过代理加认证信息的方式，成功访问了目标地址
//                            ChannelFuture responseFuture = ctx.channel().writeAndFlush(
//                                    new DefaultHttpResponse(request.protocolVersion(), new HttpResponseStatus(200, "Connection Established")));
//                            responseFuture.addListener(new ChannelFutureListener() {
//                                @Override
//                                public void operationComplete(ChannelFuture channelFuture) {
//                                    if (channelFuture.isSuccess()) {
//                                        // 移除原本的处理器，因为第一次访问成功的消息回传
//                                        ctx.pipeline().remove(HttpRequestDecoder.class);
//                                        ctx.pipeline().remove(HttpResponseEncoder.class);
//                                        ctx.pipeline().remove(HttpServerExpectContinueHandler.class);
//                                        ctx.pipeline().remove(ThreadForwardHandler.class);
//                                        // 添加新的处理器。为了去除代理请求头，重新构造新的header url ，再次发起类似 get post请求，而不是connect请求
////                                                    outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
//                                        outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
//                                        ctx.pipeline().addLast(new RelayHandler(outboundChannel));
////                                                    ctx.channel().config().setAutoRead(true);
//                                    } else {
//                                        log.error("reply tunnel established Failed: " + ctx.channel().remoteAddress() + " " + request.method() + " " + request.uri());
//                                        SocksServerUtils.closeOnFlush(ctx.channel());
//                                        SocksServerUtils.closeOnFlush(outboundChannel);
//                                    }
//                                }
//                            });
//                        }

                else {
                    // 如果尝试连接失败，就关闭连接
//                    log.info("转发请求 {} 失败，即将关闭通道, 原因：{}", request.uri(), future.cause().getMessage());
//                    ByteBuf errorMsg = Unpooled.wrappedBuffer(future.cause().getMessage().getBytes());
//                    originalChannel.writeAndFlush(errorMsg);

                    originalChannel.writeAndFlush(new DefaultHttpResponse(request.protocolVersion(), INTERNAL_SERVER_ERROR));
                    SocksServerUtils.closeOnFlush(originalChannel);
                }
                SocksServerUtils.closeOnFlush(originalChannel);
            });
        }
    }

    /**
     * 构造代理请求头的认证信息（隧道代理）
     */
    private void buildProxyAuthorizationHeaderForTunnel(HttpRequest request) {
        // 删除原有的代理认证信息，因为这个认证信息是我的服务的认证信息，不是第三方服务的代理认证信息。
        request.headers().remove("Proxy-Authorization");
        // 设置第三方服务的代理认证信息
        String tunnelProxyUsername = proxyServerConfig.getTunnelProxyUsername();
        String tunnelProxyPassword = proxyServerConfig.getTunnelProxyPassword();
        String credential = Credentials.basic(tunnelProxyUsername, tunnelProxyPassword);
        request.headers().set("Proxy-Authorization", credential);
    }

    /**
     * 获取request中的remote url
     *
     * @param httpRequest 源请求
     * @return
     */
    private String getRemoteUrl(DefaultHttpRequest httpRequest) {
        // remote url
        String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
        return HostNamePortUtils.getRemoteUrl(originalHostHeader, false);
    }

    /**
     * 获取请求头
     *
     * @param httpRequest 源请求
     * @return
     * @throws IOException
     */
    private Map<String, String> getHeaders(DefaultHttpRequest httpRequest) throws IOException {
        // 请求头
        HashMap<String, String> allHeaders = new HashMap<>();
        for (Map.Entry<String, String> header : httpRequest.headers()) {
            String headerName = header.getKey();
            String headerValue = header.getValue();
            allHeaders.put(headerName, headerValue);
        }
        return allHeaders;
    }

    /**
     * 使用 okhttp 发送代理请求
     *
     * @param httpRequest       源请求
     * @param targetUrl         目标地址
     * @param allHeaders        所有的header
     * @param proxyServerConfig 代理地址配置
     * @return 代理请求的响应
     * @throws IOException io异常
     */
    public static Response sendProxyRequestByOkHttp(DefaultHttpRequest httpRequest, String targetUrl, Map<String, String> allHeaders, ProxyServerConfig proxyServerConfig) throws IOException {

        // 获取隧道代理的 ip port username password
        String tunnelProxyUsername = proxyServerConfig.getTunnelProxyUsername();
        String tunnelProxyPassword = proxyServerConfig.getTunnelProxyPassword();
        String tunnelProxyServerIp = proxyServerConfig.getTunnelProxyServerIp();
        int tunnelProxyServerPort = proxyServerConfig.getTunnelProxyServerPort();

        // 构造代理
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(tunnelProxyServerIp, tunnelProxyServerPort));

        // 代理地址的身份认证
        Authenticator authenticator = (route, response) -> {
            String credential = Credentials.basic(tunnelProxyUsername, tunnelProxyPassword);
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };

        // 初始化 client
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(3000L, TimeUnit.MILLISECONDS)
                .readTimeout(500L, TimeUnit.MILLISECONDS)
                .proxy(proxy)
                .proxyAuthenticator(authenticator)
                .build();

        // 构建请求
        Request.Builder requestBuilder = new Request.Builder();
        // 原始header
        allHeaders.forEach(requestBuilder::header);
        // 补充header
        HEADERS.forEach(requestBuilder::addHeader);
        Request request = requestBuilder.url(targetUrl).build();

        // 发起请求
        try {
            Response response = client
                    .newCall(request)
                    .execute();
            if (response.isSuccessful()) {
                String content = Objects.requireNonNull(response.body()).string();
                FileUtils.writeStringToFile(new File(RESPONSE_HTML), content, StandardCharsets.UTF_8, false);
                log.info("okhttp client 响应成功, 内容长度：{}", content.length());
            } else {
                int code = response.code();
                String content = Objects.requireNonNull(response.body()).string();
                log.error("okhttp client 请求失败, 错误状态码：{}, content：{}", code, content);
            }
            return response;
        } catch (SocketTimeoutException e) {
            log.error("newCall:{}", e.getMessage());
        }
        return null;
    }


    /**
     * 从httprequest中寻找host和port
     * 由于不同的httpclient实现不一样，可能会有不兼容
     * 已知不兼容：
     * idea2019.3设置的http proxy: 传的Host请求头没有带上端口，因此需要以request.uri()为准 CONNECT www.google.com:443 Host=www.google.com
     * ubuntu的apt设置的代理，request.uri()为代理的地址，因此需要以Host请求头为准 CONNECT mirrors.tuna.tsinghua.edu.cn:443 Host=localhost:3128
     * 很坑。。
     *
     * @param ctx 通道上下文
     */
    private void setRealHostPort(ChannelHandlerContext ctx) {
        String hostAndPortStr = HttpMethod.CONNECT.equals(request.method()) ? request.uri() : request.headers().get("Host");
        String[] hostPortArray = hostAndPortStr.split(":");
        host = hostPortArray[0];
        String portStr = hostPortArray.length == 2 ? hostPortArray[1] : !HttpMethod.CONNECT.equals(request.method()) ? "80" : "443";
        port = Integer.parseInt(portStr);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        String clientHostname = ((InetSocketAddress) ctx.channel().remoteAddress()).getHostString();
        log.error("请求转发出现异常：{}, 目标地址：{}", cause.getMessage(), clientHostname);
        ctx.close();
    }
}
