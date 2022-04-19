package com.dataeye.proxy.arloor.web;

import com.dataeye.proxy.arloor.ArloorProxyServer;
import com.dataeye.proxy.arloor.handler.SessionHandShakeHandler;
import com.dataeye.proxy.arloor.monitor.GlobalTrafficMonitor;
import com.dataeye.proxy.arloor.monitor.MonitorService;
import com.dataeye.proxy.arloor.util.SocksServerUtils;
import com.dataeye.proxy.arloor.vo.Config;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

public class Dispatcher {
    private static final Logger log = LoggerFactory.getLogger("web");
    private static byte[] favicon = new byte[0];
    private static byte[] echarts_min_js = new byte[0];
    private static final String MAGIC_HEADER = "arloor";
    private static final MonitorService MONITOR_SERVICE = MonitorService.getInstance();
    private static Map<String, BiConsumer<HttpRequest, ChannelHandlerContext>> handler = new HashMap<String, BiConsumer<HttpRequest, ChannelHandlerContext>>() {{
        put("/favicon.ico", Dispatcher::favicon);
        put("/ip", Dispatcher::ip);
        put("/net", Dispatcher::net);
        put("/metrics", Dispatcher::metrics);
        put("/echarts.min.js", Dispatcher::echarts);
    }};

    private static void echarts(HttpRequest request, ChannelHandlerContext ctx) {
        ByteBuf buffer = ctx.alloc().buffer();
        buffer.writeBytes(echarts_min_js);
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
        response.headers().set("Server", "nginx/1.11");
        response.headers().set("Content-Length", echarts_min_js.length);
        response.headers().set("Cache-Control", "max-age=86400");
        if (needClose(request)) {
            response.headers().set(CONNECTION, CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }

    }

    private static final Map<String, Long> counters = new ConcurrentHashMap<>();

    private static void metrics(HttpRequest httpRequest, ChannelHandlerContext ctx) {
        String html = MONITOR_SERVICE.metrics();
        ByteBuf buffer = ctx.alloc().buffer();
        buffer.writeBytes(html.getBytes());
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
        response.headers().set("Server", "nginx/1.11");
        response.headers().set("Content-Length", html.getBytes().length);
        response.headers().set("Content-Type", "text/text; charset=utf-8");
        ctx.writeAndFlush(response);
    }

    private static boolean needClose(HttpRequest httpRequest) {
        Long counter = counters.computeIfAbsent(httpRequest.uri(), (key) -> 0L);
        counter++;
        if (counter > 100) {
            counter = 0L;
            counters.put(httpRequest.uri(), counter);
            return true;
        } else {
            counters.put(httpRequest.uri(), counter);
            return false;
        }
    }

    static {
        try (BufferedInputStream stream = new BufferedInputStream(Objects.requireNonNull(ArloorProxyServer.class.getClassLoader().getResourceAsStream("favicon.ico")))) {
            favicon = readAll(stream);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            log.error("缺少favicon.ico");
        }

        try (BufferedInputStream stream = new BufferedInputStream(Objects.requireNonNull(ArloorProxyServer.class.getClassLoader().getResourceAsStream("echarts.min.js")))) {
            echarts_min_js = readAll(stream);
        } catch (Throwable e) {
            log.error("加载echart.min.js失败");
        }
    }

    public static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();

//        ByteArrayOutputStream output = new ByteArrayOutputStream();
//        input.transferTo(output);
//        return output.toByteArray();
    }

    public static void handle(HttpRequest request, ChannelHandlerContext ctx) {
        SocketAddress socketAddress = ctx.channel().remoteAddress();
        boolean fromLocalAddress = ((InetSocketAddress) socketAddress).getAddress().isSiteLocalAddress();
        boolean fromLocalHost = ((InetSocketAddress) socketAddress).getAddress().isLoopbackAddress();
        // 以下允许处理：
        // 1. 来自局域网 2.无被探测风险 3. 请求头包含特定字符串
        if (fromLocalAddress || fromLocalHost || !Config.ask4Authcate || request.headers().contains(MAGIC_HEADER)) {
            log(request, ctx);
            handler.getOrDefault(request.uri(), Dispatcher::other).accept(request, ctx);
        } else {
            refuse(request, ctx);
        }
    }

    private static void other(HttpRequest request, ChannelHandlerContext ctx) {
        try {
            String path = getPath(request);
            String contentType = getContentType(path);
            RandomAccessFile randomAccessFile = new RandomAccessFile(path, "r");
            long fileLength = randomAccessFile.length();
            ChunkedFile chunkedFile = new ChunkedFile(randomAccessFile, 0, fileLength, 8192);
            // 针对其他需要读取文件的请求，增加ChunkedWriteHandler，防止OOM
            if (ctx.pipeline().get("chunked") == null) {
                ctx.pipeline().addBefore(SessionHandShakeHandler.NAME, "chunked", new ChunkedWriteHandler());
            }
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set("Server", "nginx/1.11");
            response.headers().set("Content-Length", fileLength);
            response.headers().set("Cache-Control", "max-age=1800");
            response.headers().set("Content-Type", contentType + "; charset=utf-8");
            boolean needClose = needClose(request);
            response.headers().set(CONNECTION, needClose ? CLOSE : KEEP_ALIVE);


            ctx.write(response);
            ChannelFuture sendFileFuture = null;
            sendFileFuture = ctx.write(chunkedFile, ctx.newProgressivePromise());
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationComplete(ChannelProgressiveFuture future)
                        throws Exception {
                    log.debug("Transfer complete.");
                }

                @Override
                public void operationProgressed(ChannelProgressiveFuture future,
                                                long progress, long total) throws Exception {
                    if (total < 0) {
                        log.debug("Transfer progress: " + progress);
                    } else {
                        log.debug("Transfer progress: " + progress + "/" + total);
                    }
                }
            });

            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (needClose) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } catch (FileNotFoundException fnfd) {
            r404(ctx);
        } catch (IOException e) {
            log.error("", e);
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    // 文件后缀与contentType映射见 https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Common_types
    private static String getContentType(String path) {
        final int i = path.lastIndexOf(".");
        if (i == -1) {
            return "text/text";
        }
        String end = path.substring(i);
        switch (end) {
            case ".aac": return "audio/aac";
            case ".abw": return "application/x-abiword";
            case ".arc": return "application/x-freearc";
            case ".avi": return "video/x-msvideo";
            case ".azw": return "application/vnd.amazon.ebook";
            case ".bin": return "application/octet-stream";
            case ".bmp": return "image/bmp";
            case ".bz": return "application/x-bzip";
            case ".bz2": return "application/x-bzip2";
            case ".csh": return "application/x-csh";
            case ".css": return "text/css";
            case ".csv": return "text/csv";
            case ".doc": return "application/msword";
            case ".docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".eot": return "application/vnd.ms-fontobject";
            case ".epub": return "application/epub+zip";
            case ".gif": return "image/gif";
            case ".htm": return "text/html";
            case ".html": return "text/html";
            case ".ico": return "image/vnd.microsoft.icon";
            case ".ics": return "text/calendar";
            case ".jar": return "application/java-archive";
            case ".jpeg": return "image/jpeg";
            case ".jpg": return "image/jpeg";
            case ".js": return "text/javascript";
            case ".json": return "application/json";
            case ".jsonld": return "application/ld+json";
            case ".mid": return "audio/midi";
            case ".midi": return "audio/midi";
            case ".mjs": return "text/javascript";
            case ".mp3": return "audio/mpeg";
            case ".mpeg": return "video/mpeg";
            case ".mpkg": return "application/vnd.apple.installer+xml";
            case ".odp": return "application/vnd.oasis.opendocument.presentation";
            case ".ods": return "application/vnd.oasis.opendocument.spreadsheet";
            case ".odt": return "application/vnd.oasis.opendocument.text";
            case ".oga": return "audio/ogg";
            case ".ogv": return "video/ogg";
            case ".ogx": return "application/ogg";
            case ".otf": return "font/otf";
            case ".png": return "image/png";
            case ".pdf": return "application/pdf";
            case ".ppt": return "application/vnd.ms-powerpoint";
            case ".pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".rar": return "application/x-rar-compressed";
            case ".rtf": return "application/rtf";
            case ".sh": return "application/x-sh";
            case ".svg": return "image/svg+xml";
            case ".swf": return "application/x-shockwave-flash";
            case ".tar": return "application/x-tar";
            case ".tif": return "image/tiff";
            case ".tiff": return "image/tiff";
            case ".ttf": return "font/ttf";
            case ".txt": return "text/plain";
            case ".vsd": return "application/vnd.visio";
            case ".wav": return "audio/wav";
            case ".weba": return "audio/webm";
            case ".webm": return "video/webm";
            case ".webp": return "image/webp";
            case ".woff": return "font/woff";
            case ".woff2": return "font/woff2";
            case ".xhtml": return "application/xhtml+xml";
            case ".xls": return "application/vnd.ms-excel";
            case ".xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".xml": return "application/xml";
            case ".xul": return "application/vnd.mozilla.xul+xml";
            case ".zip": return "application/zip";
            case ".3gp": return "video/3gpp";
            case ".3g2": return "video/3gpp2";
            case ".7z": return "application/x-7z-compressed";
            default: return "text/text";
        }
    }

    private static String getPath(HttpRequest request) throws UnsupportedEncodingException {
        String uri = request.uri();
        uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
        if (uri.endsWith("/")) {
            uri += "index.html";
        }
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        return uri;
    }

    private static void r404(ChannelHandlerContext ctx) {
        String notFound = "404 not found";
        ByteBuf buffer = ctx.alloc().buffer();
        buffer.writeBytes(notFound.getBytes());
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, NOT_FOUND, buffer);
        response.headers().set("Server", "nginx/1.11");
        response.headers().set("Content-Length", notFound.getBytes().length);
        response.headers().set(CONNECTION, CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void refuse(HttpRequest request, ChannelHandlerContext ctx) {
        String hostAndPortStr = request.headers().get("Host");
        if (hostAndPortStr == null) {
            SocksServerUtils.closeOnFlush(ctx.channel());
        }
        String[] hostPortArray = hostAndPortStr.split(":");
        String host = hostPortArray[0];
        String portStr = hostPortArray.length == 2 ? hostPortArray[1] : "80";
        int port = Integer.parseInt(portStr);
        String clientHostname = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        log.info("refuse!! {} {} {} {}", clientHostname, request.method(), request.uri(), String.format("{%s:%s}", host, port));
        ctx.close();
    }

    private static void ip(HttpRequest request, ChannelHandlerContext ctx) {
        String clientHostname = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        ByteBuf buffer = ctx.alloc().buffer();
        buffer.writeBytes(clientHostname.getBytes());
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
        response.headers().set("Server", "nginx/1.11");
        response.headers().set("Content-Length", clientHostname.getBytes().length);
        response.headers().set("Content-Type", "text/html; charset=utf-8");
        if (needClose(request)) {
            response.headers().set(CONNECTION, CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }

    private static void net(HttpRequest request, ChannelHandlerContext ctx) {
        String html = GlobalTrafficMonitor.html(true);
        ByteBuf buffer = ctx.alloc().buffer();
        buffer.writeBytes(html.getBytes(StandardCharsets.UTF_8));
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
        response.headers().set("Server", "nginx/1.11");
        response.headers().set("Content-Length", html.getBytes(StandardCharsets.UTF_8).length);
        response.headers().set("Content-Type", "text/html; charset=utf-8");
        if (needClose(request)) {
            response.headers().set(CONNECTION, CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }


    private static void favicon(HttpRequest request, ChannelHandlerContext ctx) {
        ByteBuf buffer = ctx.alloc().buffer();
        buffer.writeBytes(favicon);
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
        response.headers().set("Server", "nginx/1.11");
        response.headers().set("Content-Length", favicon.length);
        response.headers().set("Cache-Control", "max-age=86400");
        if (needClose(request)) {
            response.headers().set(CONNECTION, CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }


    private static final void log(HttpRequest request, ChannelHandlerContext ctx) {
        //获取Host和port
        String hostAndPortStr = request.headers().get("Host");
        if (hostAndPortStr == null) {
            SocksServerUtils.closeOnFlush(ctx.channel());
        }
        String[] hostPortArray = hostAndPortStr.split(":");
        String host = hostPortArray[0];
        String portStr = hostPortArray.length == 2 ? hostPortArray[1] : "80";
        int port = Integer.parseInt(portStr);
        String clientHostname = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        log.info("{} {} {} {}", clientHostname, request.method(), request.uri(), String.format("{%s:%s}", host, port));
    }
}
