package com.dataeye.proxy.arloor.session;

import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.handler.TunnelRelayHandler;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.arloor.handler.HeartbeatIdleStateHandler;
import com.dataeye.proxy.arloor.handler.RelayHandler;
import com.dataeye.proxy.arloor.handler.SessionHandShakeHandler;
import com.dataeye.proxy.arloor.trace.TraceConstant;
import com.dataeye.proxy.arloor.trace.Tracer;
import com.dataeye.proxy.arloor.util.OsUtils;
import com.dataeye.proxy.arloor.util.RequestUtils;
import com.dataeye.proxy.arloor.util.SocksServerUtils;
import com.dataeye.proxy.arloor.vo.Config;
import com.dataeye.proxy.arloor.web.Dispatcher;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;

public enum Status {
    /**
     * HTTP_REQUEST
     */
    HTTP_REQUEST {
        @Override
        public void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg) {
            log.info("HTTP_REQUEST");

            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                session.setRequest(request);
                String hostAndPortStr = HttpMethod.CONNECT.equals(request.method()) ? request.uri() : request.headers().get("Host");
                String[] hostPortArray = hostAndPortStr.split(":");
                String host = hostPortArray[0];
                session.setHost(host);
                String portStr = hostPortArray.length == 2 ? hostPortArray[1] : !HttpMethod.CONNECT.equals(request.method()) ? "80" : "443";
                session.setPort(Integer.parseInt(portStr));
                session.setAttribute(TraceConstant.host.name(), host);
                session.setStatus(LAST_HTTP_CONTENT);
            }
        }
    },

    /**
     * LAST_HTTP_CONTENT
     */
    LAST_HTTP_CONTENT {
        @Override
        public void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg) {
            log.info("LAST_HTTP_CONTENT");

            //SimpleChannelInboundHandler会将HttpContent中的bytebuf Release，但是这个还会转给relayHandler，所以需要在这里预先retain
            ((HttpContent) msg).content().retain();
            session.addContent((HttpContent) msg);
            if (msg instanceof LastHttpContent) {
                // 1. 如果url以 / 开头，则认为是直接请求，而不是代理请求
                if (session.getRequest().uri().startsWith("/")) {
                    session.setStatus(WEB);
                    session.handle(channelContext, msg);
                } else {
                    session.setStatus(CheckAuth);
                    session.handle(channelContext, msg);
                }
            }
        }
    },

    /**
     * WEB
     */
    WEB {
        @Override
        public void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg) {
            log.info("WEB");

            session.setAttribute(TraceConstant.host.name(), "localhost");
            Span dispatch = Tracer.spanBuilder(TraceConstant.web.name())
                    .setAttribute(TraceConstant.url.name(), String.valueOf(session.getRequest().uri()))
                    .setParent(io.opentelemetry.context.Context.current().with(session.getStreamSpan()))
                    .startSpan();
            try (Scope scope = dispatch.makeCurrent()) {
                Dispatcher.handle(session.getRequest(), channelContext);
                // 这里需要将content全部release
                session.getContents().forEach(ReferenceCountUtil::release);
            } finally {
                dispatch.end();
            }
            session.setStatus(HTTP_REQUEST);
        }
    },

    /**
     * CheckAuth
     */
    CheckAuth {
        @Override
        public void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg) {
            log.info("CheckAuth");

            String clientHostname = ((InetSocketAddress) channelContext.channel().remoteAddress()).getAddress().getHostAddress();
            //2. 检验auth
            HttpRequest request = session.getRequest();
            String basicAuth = request.headers().get("Proxy-Authorization");
            String userName = "nouser";
            Map<String, String> auths = session.getAuths();
            if (basicAuth != null && basicAuth.length() != 0) {
                String raw = auths.get(basicAuth);
                if (raw != null && raw.length() != 0) {
                    userName = raw.split(":")[0];
                }
            }
            boolean isWhiteDomain = session.isWhiteDomain(session.getHost());
            if (!isWhiteDomain && auths != null && auths.size() != 0) {
                if (basicAuth == null || !auths.containsKey(basicAuth)) {
                    log.warn(clientHostname + " " + request.method() + " " + request.uri() + "  {" + session.getHost() + "} wrong_auth:{" + basicAuth + "}");
                    // 这里需要将content全部release
                    session.getContents().forEach(ReferenceCountUtil::release);
                    DefaultHttpResponse responseAuthRequired;
                    if (Config.ask4Authcate && !request.method().equals(HttpMethod.OPTIONS) && !request.method().equals(HttpMethod.HEAD)) {
                        responseAuthRequired = new DefaultHttpResponse(request.protocolVersion(), PROXY_AUTHENTICATION_REQUIRED);
                        responseAuthRequired.headers().add("Proxy-Authenticate", "Basic realm=\"netty forwardproxy\"");
                    } else {
                        responseAuthRequired = new DefaultHttpResponse(request.protocolVersion(), INTERNAL_SERVER_ERROR);
                    }
                    channelContext.channel().writeAndFlush(responseAuthRequired);
                    SocksServerUtils.closeOnFlush(channelContext.channel());
                    Tracer.spanBuilder(TraceConstant.wrong_auth.name())
                            .setAttribute(TraceConstant.auth.name(), String.valueOf(basicAuth))
                            .setParent(io.opentelemetry.context.Context.current().with(session.getStreamSpan()))
                            .startSpan()
                            .end();
                    session.setStatus(HTTP_REQUEST);
                    return;
                }
            }

            //3. 这里进入代理请求处理，分为两种：CONNECT方法和其他HTTP方法
            log.info("{}@{} ==> {} {} {}", isWhiteDomain ? "白名单域名" : userName, clientHostname, request.method(), request.uri(), !request.uri().equals(request.headers().get("Host")) ? "Host=" + request.headers().get("Host") : "");
            if (request.method().equals(HttpMethod.CONNECT)) {
                session.setStatus(TUNNEL);
            } else {
                session.setStatus(GETPOST);
            }
            session.handle(channelContext, msg);
        }
    },

    /**
     * TUNNEL
     */
    TUNNEL {
        @Override
        public void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg) {
            log.info("TUNNEL");

            HttpRequest request = session.getRequest();
            final Channel inboundChannel = channelContext.channel();
            Bootstrap b = session.getBootStrap();
            b.group(inboundChannel.eventLoop())
                    .channel(OsUtils.socketChannelClazz())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new HeartbeatIdleStateHandler(5, 0, 0, TimeUnit.MINUTES));

            //todo 添加代理IP
            ApnProxyRemote apnProxyRemote = channelContext.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            log.info("TUNNEL, to {} for {}", apnProxyRemote.getRemote(), request.uri());
            b.connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        if (apnProxyRemote.isAppleyRemoteRule()) {

//            b.connect(session.getHost(), session.getPort()).addListener(new ChannelFutureListener() {
//                @Override
//                public void operationComplete(ChannelFuture future) throws Exception {
//                    if (future.isSuccess()) {
//                        if (false) {

                            final Channel outboundChannel = future.channel();
                            channelContext.pipeline().remove(HttpRequestDecoder.class);
                            channelContext.pipeline().remove(HttpResponseEncoder.class);
                            channelContext.pipeline().remove(HttpServerExpectContinueHandler.class);
                            channelContext.pipeline().remove(SessionHandShakeHandler.class);

                            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
                            channelContext.pipeline().addLast(new RelayHandler(outboundChannel));
//                            outboundChannel.pipeline().addLast(new ApnProxyRelayHandler( "Remote --> UA", inboundChannel));
//                            channelContext.pipeline().addLast(new ApnProxyRelayHandler("UA --> Remote", outboundChannel));

                            log.debug("构造之前：{}", request.toString());
                            String newConnectRequest = RequestUtils.constructConnectRequestForProxyByTunnel(request, "","");
                            log.debug("构造之后：{}", newConnectRequest);
                            outboundChannel
                                    .writeAndFlush(Unpooled.copiedBuffer(newConnectRequest, CharsetUtil.UTF_8))
                                    .addListener((ChannelFutureListener) future2 -> {
                                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                                            future2.channel().read();
                                        }
                                    });

                        } else {
                            final Channel outboundChannel = future.channel();
                            String targetAddr = ((InetSocketAddress) outboundChannel.remoteAddress()).getAddress().getHostAddress();
                            session.setAttribute(TraceConstant.target.name(), targetAddr);
                            // Connection established use handler provided results
                            ChannelFuture responseFuture = channelContext.channel().writeAndFlush(
                                    new DefaultHttpResponse(request.protocolVersion(), new HttpResponseStatus(200, "Connection Established")));
                            responseFuture.addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture channelFuture) {
                                    if (channelFuture.isSuccess()) {
                                        channelContext.pipeline().remove(HttpRequestDecoder.class);
                                        channelContext.pipeline().remove(HttpResponseEncoder.class);
                                        channelContext.pipeline().remove(HttpServerExpectContinueHandler.class);
                                        channelContext.pipeline().remove(SessionHandShakeHandler.class);
                                        outboundChannel.pipeline().addLast(new RelayHandler(channelContext.channel()));
                                        channelContext.pipeline().addLast(new RelayHandler(outboundChannel));
//                                                    ctx.channel().config().setAutoRead(true);
                                    } else {
                                        log.info("reply tunnel established Failed: " + channelContext.channel().remoteAddress() + " " + request.method() + " " + request.uri());
                                        SocksServerUtils.closeOnFlush(channelContext.channel());
                                        SocksServerUtils.closeOnFlush(outboundChannel);
                                    }
                                }
                            });
                        }
                    } else {
                        // Close the connection if the connection attempt has failed.
                        channelContext.channel().writeAndFlush(
                                new DefaultHttpResponse(request.protocolVersion(), INTERNAL_SERVER_ERROR)
                        );
                        SocksServerUtils.closeOnFlush(channelContext.channel());
                    }
                }
            });
            session.setStatus(WAIT_ESTABLISH);
        }
    },

    /**
     * GETPOST
     */
    GETPOST {
        @Override
        public void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg) {
            log.info("GETPOST");

            HttpRequest request = session.getRequest();
            final Channel inboundChannel = channelContext.channel();

//            TempHandler.RemoteChannelInactiveCallback cb = (remoteChannelCtx, inactiveRemoteAddr) -> {
//                log.info("Remote channel: " + inactiveRemoteAddr + " inactive, and flush end");
//                inboundChannel.close();
//            };

            Bootstrap b = session.getBootStrap();
            b.group(inboundChannel.eventLoop())
                    .channel(OsUtils.socketChannelClazz())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
//                    .handler(new ChannelInitializer<SocketChannel>() {
//                        @Override
//                        protected void initChannel(SocketChannel ch) throws Exception {
//                            ChannelPipeline pipeline = ch.pipeline();
//                            pipeline.addLast(new ApnProxyRelayHandler( "Remote --> UA", inboundChannel));
//                        }
//                    });
//                    .handler(new TempChannelInitializer(inboundChannel, request.headers().get("Host"),cb));
                    .handler(new HeartbeatIdleStateHandler(5, 0, 0, TimeUnit.MINUTES));

            //todo 添加代理IP
            ApnProxyRemote apnProxyRemote = channelContext.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            log.info("GETPOST, to {} for {}", apnProxyRemote.getRemote(), request.headers().get("Host"));
            b.connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        if (apnProxyRemote.isAppleyRemoteRule()) {

//            b.connect(session.getHost(), session.getPort()).addListener(new ChannelFutureListener() {
//                @Override
//                public void operationComplete(ChannelFuture future) throws Exception {
//                    if (future.isSuccess()) {
//                        if (false) {

                            final Channel outboundChannel = future.channel();
                            channelContext.pipeline().remove(HttpRequestDecoder.class);
                            channelContext.pipeline().remove(HttpResponseEncoder.class);
                            channelContext.pipeline().remove(HttpServerExpectContinueHandler.class);
                            channelContext.pipeline().remove(SessionHandShakeHandler.class);

//                            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
//                            channelContext.pipeline().addLast(new RelayHandler(outboundChannel));
//                            outboundChannel.pipeline().addLast(new ApnProxyRelayHandler( "Remote --> UA", inboundChannel));
                            channelContext.pipeline().addLast(new TunnelRelayHandler(new RequestMonitor(), "UA --> Remote", outboundChannel));

                            log.debug("构造之前：{}", request.toString());
                            String newRequest = RequestUtils.constructRequestStringByForward(request);
                            log.debug("构造之后：{}", newRequest);
                            outboundChannel
                                    .writeAndFlush(Unpooled.copiedBuffer(newRequest, CharsetUtil.UTF_8))
                                    .addListener((ChannelFutureListener) future2 -> {
                                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                                            future2.channel().read();
                                        }
                                    });
                            // ------------------------------------------------------------------------------------

//                            final Channel outboundChannel = future.channel();
//                            channelContext.pipeline().remove(HttpRequestDecoder.class);
//                            channelContext.pipeline().remove(HttpResponseEncoder.class);
//                            channelContext.pipeline().remove(HttpServerExpectContinueHandler.class);
//                            channelContext.pipeline().remove(SessionHandShakeHandler.class);
////                            outboundChannel.pipeline().addLast("codec", new HttpClientCodec());
////                            outboundChannel.pipeline().addLast("http_proxy_agg", new HttpObjectAggregator(1024*1204));
////                            outboundChannel.pipeline().addLast(HttpProxyHandler.HANDLER_NAME,
////                                    new TempHandler(inboundChannel, request.headers().get("Host"), cb));
//
//                            log.debug("构造之前：{}", request.toString());
//                            HttpRequest newRequest = RequestUtils.constructRequestForProxyByForward(request, "","", apnProxyRemote.isAppleyRemoteRule());
//                            log.debug("构造之后：{}", newRequest.toString());
//                            future.channel().write(newRequest);
//
////                            future.channel().write(msg);
////                            for (HttpContent hc : httpContentBuffer) {
////                                future.channel().writeAndFlush(hc);
////                            }
////                            httpContentBuffer.clear();
//
//                            // EMPTY_BUFFER 标识会让通道自动关闭
//                            future.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
//                                    .addListener((ChannelFutureListener) future1 -> future1.channel().read());

                            // ------------------------------------------------------------------------------------

//                            final Channel outboundChannel = future.channel();
//                            channelContext.pipeline().remove(HttpRequestDecoder.class);
//                            channelContext.pipeline().remove(HttpResponseEncoder.class);
//                            channelContext.pipeline().remove(HttpServerExpectContinueHandler.class);
//                            channelContext.pipeline().remove(SessionHandShakeHandler.class);
////                            outboundChannel.pipeline().addLast(new HttpClientCodec());
//                            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
//                            channelContext.pipeline().addLast(new RelayHandler(outboundChannel));
//                            log.debug("构造之前：{}", request.toString());
//                            HttpRequest newRequest = RequestUtils.constructRequestForProxyByForward(request, "","", apnProxyRemote.isAppleyRemoteRule());
//                            log.debug("构造之后：{}", newRequest.toString());
//                            outboundChannel
//                                    .writeAndFlush(newRequest)
//                                    .addListener((ChannelFutureListener) future2 -> {
//                                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
//                                            future2.channel().read();
//                                        }
//                                    });

                            // ------------------------------------------------------------------------------------

//                            channelContext.pipeline().remove(SessionHandShakeHandler.class);
//                            channelContext.pipeline().remove(HttpResponseEncoder.class);
//                            outboundChannel.pipeline().addLast(new HttpRequestEncoder());
//                            outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
//                            RelayHandler clientEndtoRemoteHandler = new RelayHandler(outboundChannel);
//                            channelContext.pipeline().addLast(clientEndtoRemoteHandler);
//                            //出于未知的原因，不知道为什么fireChannelread不行
//                            clientEndtoRemoteHandler.channelRead(channelContext, request);
//                            session.getContents().forEach(content -> {
//                                try {
//                                    clientEndtoRemoteHandler.channelRead(channelContext, content);
//                                } catch (Exception e) {
//                                    log.error("处理非CONNECT方法的代理请求失败！", e);
//                                }
//                            });

                        } else {
                            final Channel outboundChannel = future.channel();
                            String targetAddr = ((InetSocketAddress) outboundChannel.remoteAddress()).getAddress().getHostAddress();
                            session.setAttribute(TraceConstant.target.name(), targetAddr);
                            // Connection established use handler provided results

                            // 这里有几率抛出NoSuchElementException，原因是连接target host完成时，客户端已经关闭连接。
                            // 考虑到是比较小的几率，不catch。注：该异常没有啥影响。
                            channelContext.pipeline().remove(SessionHandShakeHandler.class);
                            channelContext.pipeline().remove(HttpResponseEncoder.class);
                            outboundChannel.pipeline().addLast(new HttpRequestEncoder());
                            outboundChannel.pipeline().addLast(new RelayHandler(channelContext.channel()));
                            RelayHandler clientEndtoRemoteHandler = new RelayHandler(outboundChannel);
                            channelContext.pipeline().addLast(clientEndtoRemoteHandler);
//                                        ctx.channel().config().setAutoRead(true);

                            //出于未知的原因，不知道为什么fireChannelread不行
                            clientEndtoRemoteHandler.channelRead(channelContext, request);
                            session.getContents().forEach(content -> {
                                try {
                                    clientEndtoRemoteHandler.channelRead(channelContext, content);
                                } catch (Exception e) {
                                    log.error("处理非CONNECT方法的代理请求失败！", e);
                                }
                            });
                        }

                    } else {
                        // Close the connection if the connection attempt has failed.
                        channelContext.channel().writeAndFlush(
                                new DefaultHttpResponse(request.protocolVersion(), INTERNAL_SERVER_ERROR)
                        );
                        SocksServerUtils.closeOnFlush(channelContext.channel());
                    }
                }
            });
            session.setStatus(WAIT_ESTABLISH);
        }
    },

    /**
     * 等待到target的连接建立前不应该有新请求进入
     */
    WAIT_ESTABLISH {
        @Override
        public void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg) {
            log.error("receive new message before tunnel is established, msg: {}", msg);
        }
    };

    private static final Logger log = LoggerFactory.getLogger(Status.class);
//    private static final Logger LOG = MyLogbackRollingFileUtil.getLogger("ArloorProxyServer");

    public abstract void handle(Session session, ChannelHandlerContext channelContext, HttpObject msg);
}
