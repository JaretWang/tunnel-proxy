package com.dataeye.proxy.utils;

import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/4/20 14:50
 * @description
 */
public class OkHttpTool {

    private static final OkHttpClient OKHTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();
    private static final MediaType MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    /**
     * OKHTTP  POST 请求
     *
     * @param reqUrl     地址
     * @param header     头部信息
     * @param jsonObject 请求参数
     * @return
     */
    public static String doPost(String reqUrl, Map<String, String> header, JSONObject jsonObject) {
        RequestBody body = RequestBody.create(MEDIA_TYPE, jsonObject.toString());
        // 添加头部信息
        Request.Builder builder = new Request.Builder().url(reqUrl);
        if (header != null && !header.isEmpty()) {
            header.entrySet().forEach(res -> {
                builder.addHeader(res.getKey(), res.getValue());
            });
        }
        // 发送请求
        Request request = builder.post(body).build();
        try {
            Response response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("HTTP POST同步请求失败 URL:" + reqUrl, e);
        }
    }

    /**
     * OKHTTP GET 请求
     *
     * @param reqUrl 地址
     * @param params 参数
     * @param flag   地址中带有参数为 TRUE 没有参数 FALSE
     * @return
     */
    public static String doGet(String reqUrl, Map<String, String> params, boolean flag) {
        StringBuilder stringBuilder = new StringBuilder();
        //处理参数
        if (params != null && !params.isEmpty()) {
            params.keySet().forEach(res -> {
                if (StringUtils.isNotBlank(stringBuilder) || flag) {
                    stringBuilder.append("&");
                } else {
                    stringBuilder.append("?");
                }
                try {
                    stringBuilder.append(String.format("%s=%s", res, URLEncoder.encode(params.get(res), "UTF-8")));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            });
        }
        // 拼接参数
        String requestUrl = reqUrl + stringBuilder;
        // 发送请求
        Request request = new Request.Builder()
                .url(requestUrl)
                .build();
        try {
            Response response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("HTTP GET同步请求失败 URL:" + reqUrl, e);
        }
    }

    /**
     * 使用代理IP发送
     *
     * @param targetUrl 目标地址
     * @param proxyIp   代理IP
     * @param proxyPort 代理port
     * @param username  用户名
     * @param password  密码
     * @return
     * @throws IOException
     */
    public static Response sendByProxy(String targetUrl, String proxyIp, int proxyPort, String username, String password) throws IOException {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        Authenticator authenticator = (route, response) -> {
            String credential = Credentials.basic(username, password);
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
        clientBuilder.proxyAuthenticator(authenticator);

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
        clientBuilder.proxy(proxy);
        clientBuilder.connectTimeout(60, TimeUnit.SECONDS);
        clientBuilder.callTimeout(60, TimeUnit.SECONDS);

        Request request = new Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
//                .addHeader("Connection", "Keep-Alive")
                .addHeader("Connection", "close")
                .build();

        OkHttpClient client = clientBuilder.build();

        return client.newCall(request).execute();
    }

}
