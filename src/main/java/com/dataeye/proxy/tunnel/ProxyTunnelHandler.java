package com.dataeye.proxy.tunnel;

import com.dataeye.proxy.bean.ProxyRemote;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.utils.HostNamePortUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

/**
 * @author jaret
 * @date 2022/3/18 15:51
 * @description
 */
@Slf4j
public class ProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel";
    @Resource
    private IpSelector ipSelector;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;
            // 获取原始请求的请求头，ip，port
            String originalHostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtils.getHostName(originalHostHeader);
            int originalPort = HostNamePortUtils.getPort(originalHostHeader, 443);
            // 构建真实ip和代理ip的映射关系
            ProxyRemote proxyAddress = ipSelector.getProxyAddress(originalHost, originalPort);
            // 使用代理ip再次发送请求
            sendProxyRequest(ctx, proxyAddress, originalHost, originalPort);
        }
        // 从InBound里读取的ByteBuf要手动释放，还有自己创建的ByteBuf要自己负责释放。这两处要调用这个release方法。
        // write Bytebuf到OutBound时由netty负责释放，不需要手动调用release
        ReferenceCountUtil.release(msg);
    }

    /**
     * 发送代理请求
     */
    void sendProxyRequest(ChannelHandlerContext ctx, ProxyRemote proxyRemote, String originalHost, int originalPort) {
        Channel uaChannel = ctx.channel();

        String remoteHost = proxyRemote.getRemoteHost();
        int remotePort = proxyRemote.getRemotePort();

        log.info("发送代理请求");
        // connect remote
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(uaChannel.eventLoop())
                .localAddress(remoteHost, remotePort)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ProxyTunnelChannelInitializer(proxyRemote, uaChannel))
//                .connect(proxyRemote.getRemoteHost(), proxyRemote.getRemotePort())
                .connect(originalHost, originalPort)
                .addListener(proxyRequestListener(ctx));
    }

    ChannelFutureListener proxyRequestListener(ChannelHandlerContext ctx) {
        return future1 -> {
            if (future1.isSuccess()) {
                // successfully connect to the original server
                // send connect success msg to client
                HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, new HttpResponseStatus(200,
                        "Connection established"));
                ctx.writeAndFlush(proxyConnectSuccessResponse);
            } else {
                if (ctx.channel().isActive()) {
                    ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                            .addListener(ChannelFutureListener.CLOSE);
                }
            }
        };
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("隧道处理器出现异常: {}", cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

}
