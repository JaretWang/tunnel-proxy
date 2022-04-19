package com.dataeye.proxy.arloor.handler;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/4/17 17:19
 * @description
 */
public class TempHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.proxy";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("HttpProxyHandler");
    private final Channel uaChannel;
    private final String remoteAddr;
    private final RemoteChannelInactiveCallback remoteChannelInactiveCallback;

    public TempHandler(Channel uaChannel, String remoteAddr,
                       RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.uaChannel = uaChannel;
        this.remoteAddr = remoteAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Remote channel: " + remoteAddr + " active");
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        HttpObject ho = (HttpObject) msg;
        logger.info("Recive From: " + remoteAddr + ", " + ho.getClass().getName());

        if (ho instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) ho;
            logger.info("HttpProxyHandler -> HttpResponse:{}", httpResponse.toString());
            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.KEEP_ALIVE);

            //todo 使用短连接
//            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
//            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.CLOSE);
        }

        if (ho instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) ho;
            logger.info("HttpProxyHandler -> HttpContent retain:{}", httpContent.toString());
            httpContent.retain();
//            ((HttpContent) ho).retain();
        }
        if (uaChannel.isActive()) {
            uaChannel.writeAndFlush(ho)
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            ctx.read();
                            ctx.fireChannelRead(msg);

//                            // todo 临时补充
//                            ctx.close();
                        } else {
                            ReferenceCountUtil.release(msg);
                            ctx.close();
                        }

//                        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
//                        requestMonitor.setCost(System.currentTimeMillis() - requestMonitor.getBegin());
//                        logger.info("{} ms, {}, {}, {}, {}, {}, {}",
//                                requestMonitor.getCost(),
//                                requestMonitor.isSuccess(),
//                                requestMonitor.getTunnelName(),
//                                requestMonitor.getProxyAddr(),
//                                requestMonitor.getRequestType(),
//                                requestMonitor.getTargetAddr(),
//                                requestMonitor.getFailReason());
                    });
        }


//        if (msg instanceof FullHttpResponse) {
//            FullHttpResponse httpResponse = (FullHttpResponse) msg;
////            logger.info("HttpProxyHandler -> httpResponse:{}", httpResponse.toString());
//            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.KEEP_ALIVE);
//
////            //todo 使用短连接
////            httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
////            httpResponse.headers().set("Proxy-Connection", HttpHeaders.Values.CLOSE);
//            httpResponse.retain();
//
//            if (uaChannel.isActive()) {
//                uaChannel.writeAndFlush(httpResponse)
//                        .addListener((ChannelFutureListener) future -> {
//                            if (future.isSuccess()) {
//                                ctx.read();
//                                ctx.fireChannelRead(msg);
////                            // todo 临时补充
////                            ctx.close();
//                            } else {
//                                ReferenceCountUtil.release(msg);
//                                ctx.close();
//                            }
//                        });
//            }
//        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        logger.info("Remote channel: " + remoteAddr + " inactive");

        uaChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                remoteChannelInactiveCallback.remoteChannelInactiveCallback(ctx, remoteAddr);
            }
        });
        ctx.fireChannelInactive();

//        //todo 增加
////        ctx.channel().close();
        ctx.close();
//        uaChannel.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        //todo 增加
//        ctx.channel().close();
        ctx.close();
    }

    public interface RemoteChannelInactiveCallback {
        void remoteChannelInactiveCallback(ChannelHandlerContext remoteChannelCtx,
                                           String remoeAddr) throws Exception;
    }

}