package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.HandlerCons;
import com.dataeye.proxy.utils.HttpErrorUtils;
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

    private final ProxyServerConfig proxyServerConfig;
    private final TunnelInstance tunnelInstance;
    HttpRequest httpRequest;
    Object httpRequest2;

    public TunnelProxySchemaHandler(ProxyServerConfig proxyServerConfig, TunnelInstance tunnelInstance) {
        this.proxyServerConfig = proxyServerConfig;
        this.tunnelInstance = tunnelInstance;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {

        if (msg instanceof FullHttpRequest) {
            log.debug("前置处理..............");
            FullHttpRequest httpRequest = (FullHttpRequest) msg;
            log.debug("TunnelProxySchemaHandler 接收到请求内容: {}", httpRequest.toString());

            if (httpRequest.method().equals(HttpMethod.CONNECT)) {
                ctx.pipeline().remove(TunnelProxyForwardHandler.HANDLER_NAME);

                HttpHeaders headers = httpRequest.headers();
                String authInfo = headers.get(HandlerCons.HEADER_PROXY_AUTHORIZATION, "");
                if (StringUtils.isNotBlank(authInfo)) {
                    log.info("CONNECT 请求携带的认证信息 Proxy-Authorization: {}", authInfo);
                    boolean status = checkAuth(tunnelInstance, authInfo);
                    if (!status) {
                        String errorMsg = "Incorrect authentication info";
                        ctx.writeAndFlush(HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, errorMsg));
                        SocksServerUtils.closeOnFlush(ctx.channel());
                    }
                    headers.remove("Proxy-Authorization");
                    headers.add("Proxy-Authorization", Credentials.basic(proxyServerConfig.getProxyUserName(), proxyServerConfig.getProxyPassword()));
                } else {
                    log.warn("CONNECT 请求没有认证信息，即将关闭通道");
                    String errorMsg = "missing " + HandlerCons.HEADER_PROXY_AUTHORIZATION;
                    ctx.writeAndFlush(HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, errorMsg));
                    SocksServerUtils.closeOnFlush(ctx.channel());
                }
            }
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * 检查认证信息
     *
     * @param tunnelInstance
     * @param authInfo
     * @return
     */
    private boolean checkAuth(TunnelInstance tunnelInstance, String authInfo) {
        String proxyUsername = tunnelInstance.getProxyUsername();
        String proxyPassword = tunnelInstance.getProxyPassword();
        String basic = Credentials.basic(proxyUsername, proxyPassword);
        return StringUtils.equals(basic, authInfo);
    }
}
