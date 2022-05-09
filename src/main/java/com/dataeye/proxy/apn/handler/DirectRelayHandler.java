package com.dataeye.proxy.apn.handler;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/5/9 15:41
 */
public class DirectRelayHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.proxy";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
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
        logger.info("DirectRelayHandler channelActive: Remote channel: {} active", remoteAddr);
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        logger.debug("DirectRelayHandler channelRead");

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse httpResponse = (FullHttpResponse) msg;
//            logger.info("HttpProxyHandler -> httpResponse:{}", httpResponse.toString());
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
                                    requestMonitor.setSuccess(true);
                                    ReqMonitorUtils.cost(requestMonitor, "DirectRelayHandler isSuccess");
                                    IpMonitorUtils.invoke(requestMonitor, true, "DirectRelayHandler isSuccess");
                                    first = false;
                                }
                            } else {
                                ReferenceCountUtil.release(msg);
                                ctx.close();

                                if (first) {
                                    // 临时增加
                                    requestMonitor.setSuccess(false);
                                    requestMonitor.setFailReason(future.cause().getMessage());
                                    ReqMonitorUtils.cost(requestMonitor, "DirectRelayHandler isError");
                                    IpMonitorUtils.invoke(requestMonitor, false, "DirectRelayHandler isError");
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
        logger.info("DirectRelayHandler channelReadComplete");
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        logger.info("DirectRelayHandler channelInactive, Remote channel: {} inactive", remoteAddr);
        uaChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener((ChannelFutureListener) future ->
                        remoteChannelInactiveCallback.remoteChannelInactiveCallback(ctx, remoteAddr));
        ctx.fireChannelInactive();
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("DirectRelayHandler exceptionCaught: {}", cause.getMessage());
        ctx.close();

        requestMonitor.setSuccess(false);
        requestMonitor.setFailReason(cause.getMessage());
        ReqMonitorUtils.cost(requestMonitor, "DirectRelayHandler exceptionCaught");
        IpMonitorUtils.invoke(requestMonitor, false, "DirectRelayHandler exceptionCaught");
    }

    public interface RemoteChannelInactiveCallback {
        void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                           String remoeAddr) throws Exception;
    }

}
