package com.dataeye.proxy.utils;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author jaret
 * @date 2022/4/18 10:23
 * @description
 */
public class ReqUtils {

    public static String getHost(HttpRequest request){
        String hostAndPortStr = HttpMethod.CONNECT.equals(request.method()) ? request.uri() : request.headers().get("Host");
        String[] hostPortArray = hostAndPortStr.split(":");
        if (hostAndPortStr.length() > 0) {
            return hostPortArray[0];
        }
        return "";
    }

    public static int getPort(HttpRequest request){
        String hostAndPortStr = HttpMethod.CONNECT.equals(request.method()) ? request.uri() : request.headers().get("Host");
        String[] hostPortArray = hostAndPortStr.split(":");
        String portStr = hostPortArray.length == 2 ? hostPortArray[1] : !HttpMethod.CONNECT.equals(request.method()) ? "80" : "443";
        return Integer.parseInt(portStr);
    }

}
