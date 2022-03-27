package com.dataeye.proxy.tunnel.handler;

import com.dataeye.proxy.cons.ProxyConstants;
import com.dataeye.proxy.utils.SHA256Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;

/**
 * @author jaret
 * @date 2022/3/25 19:12
 * @description
 */
@Slf4j
public class TunnelCacheFindHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "tunnel_proxy_cache_find";

    private boolean cacheFound = false;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        HttpObject ho = (HttpObject) msg;

        if (ho instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) ho;

            String url = httpRequest.getUri();

            File cacheDir = new File(ProxyConstants.CACHE_DIR + "/" + SHA256Utils.hash(url));

            if (cacheDir.exists()) {
                cacheFound = true;

                if (log.isInfoEnabled()) {
                    log.info(url + " cache found!");
                }

                writeCacheResponse(cacheDir, ctx);

                ReferenceCountUtil.release(msg);
            } else {
                cacheFound = false;
                ctx.fireChannelRead(msg);
            }
        } else {
            if (cacheFound) {
                ReferenceCountUtil.release(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }

    private void writeCacheResponse(File cacheDir, ChannelHandlerContext ctx) {
        File headerInfoFile = new File(cacheDir, "headerinfo");
        Properties headerProperties = new Properties();

        try {
            headerProperties.load(new FileInputStream(headerInfoFile));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        File cacheDataDir = new File(cacheDir, ProxyConstants.CACHE_DATA_DIR);
        File[] cacheDataFiles = cacheDataDir.listFiles();
        Arrays.sort(cacheDataFiles, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        HttpResponse cacheResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        HttpHeaders.setHeader(cacheResponse, "X-APN-PROXY-CACHE", cacheDir.getName());
        if (headerProperties.containsKey(HttpHeaders.Names.CONTENT_LENGTH)) {
            HttpHeaders.setHeader(cacheResponse, HttpHeaders.Names.CONTENT_LENGTH, headerProperties.getProperty(HttpHeaders.Names.CONTENT_LENGTH));
        }
        HttpHeaders.setHeader(cacheResponse, HttpHeaders.Names.CONTENT_TYPE, headerProperties.getProperty(HttpHeaders.Names.CONTENT_TYPE));
        if (headerProperties.containsKey(HttpHeaders.Names.TRANSFER_ENCODING)) {
            HttpHeaders.setHeader(cacheResponse, HttpHeaders.Names.TRANSFER_ENCODING, headerProperties.getProperty(HttpHeaders.Names.TRANSFER_ENCODING));
        }

        ctx.writeAndFlush(cacheResponse);

        for (File cacheDataFile : cacheDataFiles) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(cacheDataFile);
            } catch (FileNotFoundException e) {
                log.error(e.getMessage(), e);
            }
            if (in != null) {
                ByteBuf cacheResponseContent = Unpooled.buffer();
                byte[] buf = new byte[1024];
                try {
                    int count = -1;
                    while ((count = in.read(buf, 0, 1024)) != -1) {
                        cacheResponseContent.writeBytes(buf, 0, count);
                    }
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }

                HttpContent cacheContent = new DefaultHttpContent(cacheResponseContent);
                ctx.writeAndFlush(cacheContent);
            }

            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }

        ctx.write(new DefaultLastHttpContent());

        ctx.flush();
    }

}
