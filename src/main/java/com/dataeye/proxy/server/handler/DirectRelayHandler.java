package com.dataeye.proxy.server.handler;

import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/5/9 15:41
 */
@Slf4j
public class DirectRelayHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.proxy";
    private final Channel uaChannel;
    private final String remoteAddr;
    private final RemoteChannelInactiveCallback remoteChannelInactiveCallback;
    private final RequestMonitor requestMonitor;
    private boolean first = true;

    public DirectRelayHandler(ApnHandlerParams apnHandlerParams, Channel uaChannel, String remoteAddr,
                              RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.requestMonitor = apnHandlerParams.getRequestMonitor();
        this.uaChannel = uaChannel;
        this.remoteAddr = remoteAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("DirectRelayHandler channelActive: Remote channel: {} active", remoteAddr);
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        log.debug("DirectRelayHandler channelRead");

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse httpResponse = (FullHttpResponse) msg;
//            log.debug("HttpProxyHandler -> httpResponse:{}", httpResponse.toString());
//            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.KEEP_ALIVE);

            // 使用短连接
            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.CLOSE);
            // 引用计数 +1
            httpResponse.retain();

            if (uaChannel.isActive()) {
                uaChannel.writeAndFlush(httpResponse)
                        .addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                ctx.read();
                                // 引用计数 -1
                                ctx.fireChannelRead(msg);

                                // 依然会执行两次，应该放入 attribute key
                                if (first) {
                                    // 临时增加
                                    ReqMonitorUtils.ok(requestMonitor, "DirectRelayHandler isSuccess");
                                    IpMonitorUtils.ok(requestMonitor, "DirectRelayHandler isSuccess");
                                    first = false;
                                }
                            } else {
                                ReferenceCountUtil.release(msg);
                                ctx.close();

                                if (first) {
                                    // 临时增加
                                    ReqMonitorUtils.error(requestMonitor, "DirectRelayHandler isError", future.cause().getMessage());
                                    IpMonitorUtils.error(requestMonitor, "DirectRelayHandler isError", future.cause().getMessage());
                                    first = false;
                                }
                            }
                        });
            } else {
                // 临时增加, 修复 too many files
                ReferenceCountUtil.release(msg);
            }
        } else {
            // 临时增加, 修复 too many files
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log.debug("DirectRelayHandler channelReadComplete");
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        log.debug("DirectRelayHandler channelInactive, Remote channel: {} inactive", remoteAddr);
        uaChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener((ChannelFutureListener) future ->
                        remoteChannelInactiveCallback.remoteChannelInactiveCallback(ctx, remoteAddr));
        ctx.fireChannelInactive();
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("DirectRelayHandler异常", cause);
        ctx.close();
        ReqMonitorUtils.error(requestMonitor, "DirectRelayHandler exceptionCaught", cause.getMessage());
        IpMonitorUtils.error(requestMonitor, "DirectRelayHandler exceptionCaught",cause.getMessage());
    }

    public interface RemoteChannelInactiveCallback {
        void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                           String remoeAddr) throws Exception;
    }

}
