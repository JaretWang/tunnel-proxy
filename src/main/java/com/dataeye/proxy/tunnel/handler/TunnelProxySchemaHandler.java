package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jaret
 * @date 2022/3/25 19:10
 * @description
 */
@Slf4j
public class TunnelProxySchemaHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy_schema";

    private ProxyServerConfig proxyServerConfig;

    public TunnelProxySchemaHandler(ProxyServerConfig proxyServerConfig) {
        this.proxyServerConfig = proxyServerConfig;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {

        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            log.info("TunnelProxySchemaHandler 接收到请求内容: {}", httpRequest.toString());

            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(TunnelCacheFindHandler.HANDLER_NAME);
                ctx.pipeline().remove(TunnelProxyForwardHandler.HANDLER_NAME);
            } else {
                HttpHeaders headers = httpRequest.headers();
                String authInfo = headers.get("Proxy-Authorization", "");
                if (StringUtils.isNotBlank(authInfo)) {
                    log.info("请求带有 Proxy-Authorization, 请求类型: {}", httpRequest.method());
                    headers.remove("Proxy-Authorization");
                    String proxyUserName = proxyServerConfig.getProxyUserName();
                    String proxyPassword = proxyServerConfig.getProxyPassword();
                    headers.add("Proxy-Authorization", Credentials.basic(proxyUserName, proxyPassword));
                } else {
                    // TODO 检测是否带有认证信息
                    log.info("请求没有 Proxy-Authorization, 请求类型: {}, 即将关闭通道", httpRequest.method());
                    // 返回响应
                    HttpResponse proxyAuthRequired = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
                    ctx.writeAndFlush(proxyAuthRequired);
                    SocksServerUtils.closeOnFlush(ctx.channel());

//                    String proxyUserName = proxyServerConfig.getProxyUserName();
//                    String proxyPassword = proxyServerConfig.getProxyPassword();
//                    headers.add("Proxy-Authorization", Credentials.basic(proxyUserName, proxyPassword));
                }
            }
        }

        ctx.fireChannelRead(msg);
    }
}
