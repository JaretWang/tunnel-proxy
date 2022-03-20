//package com.dataeye.proxy.server;
//
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.netty.handler.codec.http.*;
//import io.netty.util.CharsetUtil;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * @author jaret
// * @date 2022/3/18 18:14
// * @description
// */
//@Slf4j
//public class HttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {
//
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
//
//        if (msg instanceof HttpResponse) {
//            HttpResponse response = (HttpResponse) msg;
//            log.info("STATUS: " + response.getStatus());
//            log.info("VERSION: " + response.getProtocolVersion());
//            if (!response.headers().isEmpty()) {
//                for (String name : response.headers().names()) {
//                    for (String value : response.headers().getAll(name)) {
//                        log.info("HEADER: " + name + " = " + value);
//                    }
//                }
//            }
//
//            if (HttpHeaders.isTransferEncodingChunked(response)) {
//                log.info("CHUNKED CONTENT {");
//            } else {
//                log.info("CONTENT {");
//            }
//        }
//        if (msg instanceof HttpContent) {
//            HttpContent content = (HttpContent) msg;
//            log.info(content.content().toString(CharsetUtil.UTF_8));
//            if (content instanceof LastHttpContent) {
//                log.info("} END OF CONTENT");
//            }
//        }
//        if (msg instanceof LastHttpContent) {
//            ctx.close();
//        }
//    }
//
//}
