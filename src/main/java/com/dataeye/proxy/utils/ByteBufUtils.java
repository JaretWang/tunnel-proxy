package com.dataeye.proxy.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description
 */
@Component
@Scope("prototype")
@ToString(of = {"method", "host", "port", "https"})
public class ByteBufUtils {

    @Getter
    private String method;
    @Getter
    private String host;
    @Getter
    private int port;
    @Getter
    private boolean https;
    @Getter
    private boolean complete;
    @Getter
    private ByteBuf byteBuf = Unpooled.buffer();

    private final StringBuilder lineBuf = new StringBuilder();

    /**
     * 从 ByteBuf 中提取 host port protocol
     *
     * @param in netty 缓冲流
     */
    public void digest(ByteBuf in) {
        while (in.isReadable()) {
            if (complete) {
                throw new IllegalStateException("already complete");
            }
            String line = readLine(in);
            if (line == null) {
                return;
            }
            if (method == null) {
                // the first word is http method name
                method = line.split(" ")[0];
                // method CONNECT means https
                https = method.equalsIgnoreCase("CONNECT");
            }
            if (line.startsWith("Host: ")) {
                String[] arr = line.split(":");
                host = arr[1].trim();
                if (arr.length == 3) {
                    port = Integer.parseInt(arr[2]);
                } else if (https) {
                    // https
                    port = 443;
                } else {
                    // http
                    port = 80;
                }
            }
            if (line.isEmpty()) {
                if (host == null || port == 0) {
                    throw new IllegalStateException("cannot find header \'Host\'");
                }
                byteBuf = byteBuf.asReadOnly();
                complete = true;
                break;
            }
        }
    }

    private String readLine(ByteBuf in) {
        while (in.isReadable()) {
            byte b = in.readByte();
            byteBuf.writeByte(b);
            lineBuf.append((char) b);
            int len = lineBuf.length();
            if (len >= 2 && lineBuf.substring(len - 2).equals("\r\n")) {
                String line = lineBuf.substring(0, len - 2);
                lineBuf.delete(0, len);
                return line;
            }
        }
        return null;
    }
}
