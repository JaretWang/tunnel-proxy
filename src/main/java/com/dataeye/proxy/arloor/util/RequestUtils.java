package com.dataeye.proxy.arloor.util;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import okhttp3.Credentials;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * @author jaret
 * @date 2022/4/17 0:10
 * @description
 */
public class RequestUtils {

    public static String constructRequestStringByForward(HttpRequest httpRequest) {
        String CRLF = "\r\n";
        String url = httpRequest.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append(httpRequest.getMethod().name()).append(" ").append(url).append(" ")
                .append(httpRequest.getProtocolVersion().text()).append(CRLF);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
//
//            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
//                continue;
//            }
//
//            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
//                continue;
//            }

            for (String headerValue : httpRequest.headers().getAll(headerName)) {
                sb.append(headerName).append(": ").append(headerValue).append(CRLF);
            }
        }
        sb.append(CRLF);

        return sb.toString();
    }

    public static String constructConnectRequestForProxyByTunnel(HttpRequest httpRequest, String username, String password) {
        String CRLF = "\r\n";
        String url = httpRequest.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append(httpRequest.getMethod().name()).append(" ").append(url).append(" ")
                .append(httpRequest.getProtocolVersion().text()).append(CRLF);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
//            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
//                continue;
//            }
//
//            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
//                continue;
//            }

            for (String headerValue : httpRequest.headers().getAll(headerName)) {
                sb.append(headerName).append(": ").append(headerValue).append(CRLF);
            }
        }

        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            String basic = Credentials.basic(username, password, StandardCharsets.UTF_8);
            sb.append("Proxy-Authorization: ").append(basic).append(CRLF);
        }

        sb.append(CRLF);

        return sb.toString();
    }


    public static HttpRequest constructRequestForProxyByForward(HttpRequest httpRequest, String username, String password, boolean isAppleyRemoteRule) {

        String uri = httpRequest.getUri();

        if (!isAppleyRemoteRule) {
            uri = getPartialUrl(uri);
        }

        HttpRequest _httpRequest = new DefaultHttpRequest(httpRequest.getProtocolVersion(),
                httpRequest.getMethod(), uri);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
            // todo 放开请求，更改一下请求头
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Authorization")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
                continue;
            }

            _httpRequest.headers().add(headerName, httpRequest.headers().getAll(headerName));
        }

        // todo 更改长连接为短链接
//        _httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        _httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        _httpRequest.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.IDENTITY);

        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            String basic = Credentials.basic(username, password, StandardCharsets.UTF_8);
            _httpRequest.headers().set("Proxy-Authorization", basic);
        }

        return _httpRequest;
    }

    public static String getPartialUrl(String fullUrl) {
        if (StringUtils.startsWith(fullUrl, "http")) {
            int idx = StringUtils.indexOf(fullUrl, "/", 7);
            return idx == -1 ? "/" : StringUtils.substring(fullUrl, idx);
        }

        return fullUrl;
    }

}
