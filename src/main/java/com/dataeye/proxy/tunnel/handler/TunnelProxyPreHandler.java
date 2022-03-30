package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.bean.TunnelProxyListenType;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.HandlerCons;
import com.dataeye.proxy.utils.HostNamePortUtils;
import com.dataeye.proxy.utils.HttpErrorUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jaret
 * @date 2022/3/25 18:03
 * @description 检查请求的黑白名单,包括请求的ip，port，类型
 */
@Slf4j
public class TunnelProxyPreHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy_pre";

    private static String[] forbiddenIps = new String[]{"10.", "172.16.", "172.17.",
            "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.", "192.168."};

    private static int[] forbiddenPorts = new int[]{20, 21, 22};

    private boolean isPacRequest = false;

    private ProxyServerConfig proxyServerConfig;

    public TunnelProxyPreHandler(ProxyServerConfig proxyServerConfig) {
        this.proxyServerConfig = proxyServerConfig;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (preCheck(ctx, msg)) {
            ctx.fireChannelRead(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    private boolean preCheck(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            log.debug("TunnelProxyPreHandler 接收到请求内容: {}", httpRequest.toString());

            String hostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtils.getHostName(hostHeader);

            // http rest log
            if (log.isInfoEnabled()) {
                log.info(ctx.channel().remoteAddress() + " "
                        + httpRequest.getMethod().name() + " " + httpRequest.getUri()
                        + " " + httpRequest.getProtocolVersion().text() + ", "
                        + hostHeader + ", "
                        + httpRequest.headers().get(HttpHeaders.Names.USER_AGENT));
            }

            // pac request
            if (StringUtils.equals(originalHost, proxyServerConfig.getHost())) {
                isPacRequest = true;

                String pacContent;
                if (proxyServerConfig.getTunnelProxyListenType() == TunnelProxyListenType.SSL) {
                    pacContent = buildPacForSsl();
                } else {
                    pacContent = buildPacForPlain();
                }

                ByteBuf pacResponseContent = Unpooled.copiedBuffer(pacContent, CharsetUtil.UTF_8);
                FullHttpMessage pacResponseMsg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK, pacResponseContent);
                HttpHeaders.setContentLength(pacResponseMsg, pacResponseContent.readableBytes());
                HttpHeaders.setHeader(pacResponseMsg, "X-APN-PROXY-PAC", "OK");
                HttpHeaders.setHeader(pacResponseMsg, "X-APN-PROXY-URL", "https://github.com/apn-proxy/apn-proxy");
                HttpHeaders.setHeader(pacResponseMsg, "X-APN-PROXY-MSG", "We need more commiters!");

                ctx.write(pacResponseMsg);
                ctx.flush();
                return false;
            }

            // forbid request to proxy server from internal network
            for (String forbiddenIp : forbiddenIps) {
                if (StringUtils.startsWith(originalHost, forbiddenIp)) {
                    String errorMsg = "Forbidden";
                    ctx.writeAndFlush(HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.FORBIDDEN, errorMsg));
                    return false;
                }
            }

            // forbid request to proxy server local
            if (StringUtils.equals(originalHost, HandlerCons.LOCALHOST_IP) || StringUtils.equals(originalHost, HandlerCons.LOCALHOST_NAME)) {
                String errorMsg = "Forbidden";
                ctx.writeAndFlush(HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.FORBIDDEN, errorMsg));
                return false;
            }

            // forbid reqeust to some port
            int originalPort = HostNamePortUtils.getPort(hostHeader, -1);
            for (int fobiddenPort : forbiddenPorts) {
                if (originalPort == fobiddenPort) {
                    String errorMsg = "Forbidden";
                    ctx.writeAndFlush(HttpErrorUtils.buildHttpErrorMessage(HttpResponseStatus.FORBIDDEN, errorMsg));
                    return false;
                }
            }

        } else {
            if (isPacRequest) {
                if (msg instanceof LastHttpContent) {
                    isPacRequest = false;
                }

                return false;
            }
        }

        return true;
    }

    private String buildPacForPlain() {

        StringBuilder sb = new StringBuilder();
        sb.append("function FindProxyForURL(url, host){var PROXY = \"PROXY ")
                .append(proxyServerConfig.getHost()).append(":")
                .append(proxyServerConfig.getPort()).append("\";var DEFAULT = \"DIRECT\";");

        //TODO 注释掉了 没用
//        for (ApnProxyRemoteRule remoteRule : ApnProxyConfig.getConfig().getRemoteRuleList()) {
//            for (String originalHost : remoteRule.getOriginalHostList()) {
//                if (StringUtils.isNotBlank(originalHost)) {
//                    sb.append("if(/^[\\w\\-]+:\\/+(?!\\/)(?:[^\\/]+\\.)?")
//                            .append(StringUtils.replace(originalHost, ".", "\\."))
//                            .append("/i.test(url)) return PROXY;");
//                }
//            }
//        }

        sb.append("return DEFAULT;}");

        return sb.toString();
    }

    private String buildPacForSsl() {

        StringBuilder sb = new StringBuilder();
        sb.append("function FindProxyForURL(url, host){var PROXY = \"HTTPS ")
                .append(proxyServerConfig.getHost()).append(":")
                .append(proxyServerConfig.getPort()).append("\";");

        sb.append("return PROXY;}");

        return sb.toString();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(cause.getMessage(), cause);
        ctx.close();
    }

}
