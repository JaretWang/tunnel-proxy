package com.dataeye.proxy.utils;

import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
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
}
