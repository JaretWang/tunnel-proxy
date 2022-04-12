/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.dataeye.proxy.apn.handler;


import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.cons.ApnProxyConstants;
import com.dataeye.proxy.apn.utils.SHA256Util;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Properties;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.handler.CacheSaveHandler 14-1-8 16:13 (xmx) Exp $
 */
public class CacheSaveHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("CacheSaveHandler");

    public static final String HANDLER_NAME = "apnproxy.cache.save";

    private boolean caching = false;

    private int count = 0;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        HttpObject ho = (HttpObject) msg;

        String url = ctx.channel().attr(ApnProxyConstants.REQUST_URL_ATTRIBUTE_KEY).get();
        File cacheDir = new File(ApnProxyConstants.CACHE_DIR + "/" + SHA256Util.hash(url));
        File cacheDataDir = new File(cacheDir, ApnProxyConstants.CACHE_DATA_DIR);

        long start = System.currentTimeMillis();

        if (ho instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) ho;

            caching = false;
            count = 0;

            // just test now
            if (isCacheAbleContent(httpResponse.headers().get(HttpHeaders.Names.CONTENT_TYPE)) && httpResponse.getStatus().code() == 200) {

                if (!cacheDir.exists()) {


                    caching = true;

                    cacheDataDir.mkdirs();

                    File headerInfoFile = new File(cacheDir, "headerinfo");
                    Properties headerProperties = new Properties();

                    headerProperties.put(HttpHeaders.Names.CONTENT_TYPE, httpResponse.headers().get(HttpHeaders.Names.CONTENT_TYPE));
                    if (StringUtils.isNotBlank(httpResponse.headers().get(HttpHeaders.Names.TRANSFER_ENCODING))) {
                        headerProperties.put(HttpHeaders.Names.TRANSFER_ENCODING, httpResponse.headers().get(HttpHeaders.Names.TRANSFER_ENCODING));
                    }
                    if (StringUtils.isNotBlank(httpResponse.headers().get(HttpHeaders.Names.CONTENT_LENGTH))) {
                        headerProperties.put(HttpHeaders.Names.CONTENT_LENGTH, httpResponse.headers().get(HttpHeaders.Names.CONTENT_LENGTH));
                    }

                    FileOutputStream headerInfoFileOutputStream = new FileOutputStream(headerInfoFile);
                    headerProperties.store(headerInfoFileOutputStream, url);
                    headerInfoFileOutputStream.close();
                }
            }
        } else {
            if (caching) {

                final File dataFile = new File(cacheDataDir, String.format("%05d", count++));

                HttpContent hc = ((HttpContent) msg);
                final ByteBuf byteBuf = Unpooled.copiedBuffer(hc.content().readerIndex(0));

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        FileChannel localfileChannel = null;

                        try {
                            FileOutputStream outputStream = new FileOutputStream(dataFile);
                            localfileChannel = outputStream.getChannel();

                            localfileChannel.write(byteBuf.nioBuffer());
                            localfileChannel.force(false);
                            localfileChannel.close();

                            byteBuf.release();
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        } finally {
                            if (localfileChannel != null) {
                                try {
                                    localfileChannel.close();
                                } catch (IOException e) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        }
                    }
                }).start();

            }
        }

        ReferenceCountUtil.release(msg);
    }

    private boolean isCacheAbleContent(String contentType) {
        String[] cacheContentTypeArray = new String[]{"image/jpeg", "image/png"};
        for (String cacheContentType : cacheContentTypeArray) {
            if (StringUtils.equals(contentType, cacheContentType)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }


}
