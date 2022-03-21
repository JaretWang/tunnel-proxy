package com.dataeye.proxy.server.handler;

import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.utils.ByteBufUtils;
import com.sun.org.apache.bcel.internal.generic.NEW;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description 代理服务器处理器
 */
@Component
@Scope("prototype")
@Slf4j
@NoArgsConstructor
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {

    private String id;
    private Channel clientChannel;
    private Channel remoteChannel;
    @Autowired
    private ByteBufUtils byteBufUtils;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ProxyServerConfig proxyServerConfig;
    private static final String CONNECT_ESTABLISHED = "HTTP/1.1 200 Connection Established\r\n\r\n";
    private static final String USER_AGENT = "Mozilla/5.0 xx-dev-web-common httpclient/4.x";
    private static final String RESPONSE_HTML = "C:\\Users\\王朝甲\\Desktop\\DataEye\\GitLab\\tunnel-proxy\\src\\main\\resources\\templates\\response.html";
    private static final HashMap<String, String> HEADERS = new HashMap<String, String>() {
        {
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36");
            put("Connection", "close");
        }
    };

    public ProxyServerHandler(String id) {
        this.id = id;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.debug("代理服务器建立连接...");
        clientChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
        if (byteBufUtils.isComplete()) {
            log.info("代理服务器转发数据...");
            remoteChannel.writeAndFlush(msg);
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        // 读取数据
        byteBufUtils.digest(in);
        if (!byteBufUtils.isComplete()) {
            // 释放数据
            in.release();
            return;
        }
        log.info("{} {}", id, byteBufUtils);
        // disable AutoRead until remote connection is ready
        clientChannel.config().setAutoRead(false);
        // if https, respond 200 to create tunnel
        if (byteBufUtils.isHttps()) {
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(CONNECT_ESTABLISHED.getBytes()));
        }

        String url = buildTargetUri();
        // 发送代理请求
        sendProxyRequestByOkHttp(url, ctx, proxyServerConfig);
    }

    /**
     * 构建目标uri
     *
     * @return 目标url
     */
    String buildTargetUri() {
        String remoteHost = byteBufUtils.getHost();
        int remotePort = byteBufUtils.getPort();
        log.debug("远程地址: {}, 远程端口: {}", remoteHost, remotePort);
        boolean https = byteBufUtils.isHttps();
        StringBuilder builder = new StringBuilder();
        if (https) {
            builder.append("https://");
        } else {
            builder.append("http://");
        }
        return builder.append(remoteHost).append(":").append(remotePort).toString();
    }

    /**
     * 使用 okhttp 发送代理请求
     *
     * @param targetUrl         需要的访问的目标地址
     * @param ctx               通道处理器上下文
     * @param proxyServerConfig 代理服务配置
     * @throws IOException 抛出异常
     */
    public static void sendProxyRequestByOkHttp(String targetUrl, ChannelHandlerContext ctx, ProxyServerConfig proxyServerConfig) throws IOException {
        // 获取隧道代理的 ip port username password
        String tunnelProxyUsername = proxyServerConfig.getTunnelProxyUsername();
        String tunnelProxyPassword = proxyServerConfig.getTunnelProxyPassword();
        String tunnelProxyServerIp = proxyServerConfig.getTunnelProxyServerIp();
        int tunnelProxyServerPort = proxyServerConfig.getTunnelProxyServerPort();

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(tunnelProxyServerIp, tunnelProxyServerPort));

        Authenticator authenticator = (route, response) -> {
            String credential = Credentials.basic(tunnelProxyUsername, tunnelProxyPassword);
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy)
                .proxyAuthenticator(authenticator)
                .build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(targetUrl);
        HEADERS.forEach(requestBuilder::addHeader);
        Request request = requestBuilder.build();

        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            String content = Objects.requireNonNull(response.body()).string();
            FileUtils.writeStringToFile(new File(RESPONSE_HTML), content, StandardCharsets.UTF_8, false);
            log.info("okhttp client 响应成功, 内容长度：{}", content.length());
        } else {
            int code = response.code();
            String content = Objects.requireNonNull(response.body()).string();
            log.error("okhttp client 请求失败, 错误状态码：{}, content：{}", code, content);
        }
        ctx.writeAndFlush(response);
    }

    /**
     * 使用 http client 发送代理请求
     *
     * @param targetUrl 需要的访问的目标地址
     * @param ctx       通道处理器上下文
     */
    private void sendProxyRequestByHttpClient(String targetUrl, ChannelHandlerContext ctx) throws IOException {
        // 配置 httpClient
        ConnectionConfig connectionConfig = ConnectionConfig.custom().setCharset(Consts.UTF_8).build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(2000);
        cm.setDefaultMaxPerRoute(40);
        cm.setDefaultConnectionConfig(connectionConfig);
        CloseableHttpClient httpClient = HttpClients.custom()
                .setUserAgent(USER_AGENT).setConnectionManager(cm)
                .disableContentCompression().disableCookieManagement().build();

        // 获取隧道代理 ip port
        String tunnelProxyServerIp = proxyServerConfig.getTunnelProxyServerIp();
        int tunnelProxyServerPort = proxyServerConfig.getTunnelProxyServerPort();

        // 构造代理请求
        HttpHost proxy = new HttpHost(tunnelProxyServerIp, tunnelProxyServerPort);

        // 构造请求
        RequestConfig config = RequestConfig.custom().setProxy(proxy)
                .setExpectContinueEnabled(true).setConnectionRequestTimeout(5000)
                .setConnectTimeout(10000).setSocketTimeout(10000)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
        HttpGet request = new HttpGet(targetUrl);
        request.setConfig(config);

        // 发送请求
        CloseableHttpResponse httpResponse = httpClient.execute(request);
        ctx.writeAndFlush(httpResponse);

    }

    /**
     * 使用 netty client 发送代理请求
     *
     * @param in 缓冲字节
     */
    private void sendProxyRequestByNetty(ByteBuf in, String remoteUrl, int remotePort) {
        log.info("发送代理请求, 远程地址: {}, 远程端口: {}", remoteUrl, remotePort);

        // 创建一个客户端
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientChannel.eventLoop())
                .channel(clientChannel.getClass())
                .remoteAddress(remoteUrl, remotePort)
                .handler(applicationContext.getBean(ProxyClientRemoteHandler.class, id, clientChannel));

        // 开始连接隧道代理地址
        String tunnelProxyServerIp = proxyServerConfig.getTunnelProxyServerIp();
        int tunnelProxyServerPort = proxyServerConfig.getTunnelProxyServerPort();
        ChannelFuture remoteFuture = bootstrap.connect(tunnelProxyServerIp, tunnelProxyServerPort);
        remoteChannel = remoteFuture.channel();

        // 远程连接监听
        remoteFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // 连接已经准备就绪，开启自动读取
                clientChannel.config().setAutoRead(true);
                if (!byteBufUtils.isHttps()) {
                    // 转发请求头和剩下的字节
                    remoteChannel.write(byteBufUtils.getByteBuf());
                }
                remoteChannel.writeAndFlush(in);
            } else {
                in.release();
                clientChannel.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("代理服务器断开连接...");
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        log.error("{} 代理服务器发生异常 : {}", id, e.getMessage());
        e.printStackTrace();
        flushAndClose(clientChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
