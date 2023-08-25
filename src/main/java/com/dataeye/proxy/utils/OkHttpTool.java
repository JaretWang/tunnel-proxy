package com.dataeye.proxy.utils;

import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.TunnelInitService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/4/20 14:50
 * @description
 */
@Slf4j
@Component
public class OkHttpTool {

    private static final OkHttpClient OKHTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build();
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    @Autowired
    TunnelInitService tunnelInitService;

    /**
     * OKHTTP  POST 请求
     *
     * @param reqUrl     地址
     * @param header     头部信息
     * @param jsonObject 请求参数
     * @return
     */
    public static String doPost(String reqUrl, Map<String, String> header, JSONObject jsonObject) {
        Request request = buildPostRequest(reqUrl, header, jsonObject.toJSONString().getBytes());
        try {
            Response response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            log.error("doPost error, url:{}", reqUrl, e);
            return "";
        }
    }

    public static String doGet(String reqUrl) {
        return doGet(reqUrl, null, false);
    }

    /**
     * OKHTTP GET 请求
     *
     * @param reqUrl 地址
     * @param params 参数
     * @param flag   地址中带有参数为 TRUE 没有参数 FALSE
     * @return
     */
    public static String doGet(String reqUrl, Map<String, String> header, Map<String, String> params, boolean flag) {
        // params
        appendParams(reqUrl, params);
        // reqyest
        Request request = buildGetRequest(reqUrl, header);
        try {
            Response response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            log.error("doGet error, url:{}", reqUrl, e);
            return "";
        }
    }

    /**
     * 参数使用 ? 拼接在url后面
     *
     * @param reqUrl
     * @param params
     * @param flag
     * @return
     */
    public static String doGet(String reqUrl, Map<String, String> params, boolean flag) {
        // params
        appendParams(reqUrl, params);
        // reqyest
        Request request = buildGetRequest(reqUrl, null);
        Response response = null;
        try {
            response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            log.error("doGet error, url:{}", reqUrl, e);
            return "";
        } finally {
            closeResponse(response);
        }
    }

    public static void closeResponse(Response response) {
        if (Objects.nonNull(response)) {
            response.close();
        }
    }

    private static Request buildGetRequest(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get();
        if (headers != null && !headers.isEmpty()) {
            builder.headers(Headers.of(headers));
        }
        return builder.build();
    }

    private static Request buildGetRequestWithBody(String url, Map<String, String> headers, byte[] body) {
        Request.Builder builder = new Request.Builder()
                .url(url)
//                .patch()
//                .put()
                .get();
        if (headers != null && !headers.isEmpty()) {
            builder.headers(Headers.of(headers));
        }
//        if (body != null && body.length > 0 && headers != null && !headers.isEmpty()) {
//            String contentType = headers.getOrDefault("Content-Type", "");
//            MediaType mediaType;
//            if (StringUtils.isNotBlank(contentType)) {
//                mediaType = MediaType.get(contentType);
//                builder.setBody$okhttp(RequestBody.create(mediaType, body));
//            } else {
//                mediaType = DEFAULT_MEDIA_TYPE;
//                builder.setBody$okhttp(RequestBody.create(mediaType, Arrays.toString(body))).build();
//            }
//        }
        Request build = builder.build();
        return build;
    }

    private static Request buildPostRequest(String url, Map<String, String> headers, byte[] body) {
        Request.Builder builder = new Request.Builder()
                .url(url);
        if (headers != null && !headers.isEmpty()) {
            builder.headers(Headers.of(headers));
        }
        if (body != null && body.length > 0 && headers != null && !headers.isEmpty()) {
            // 为了避免Content-Type为小写，却get不到这个值
            String contentType = "";
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                if ("Content-Type".equalsIgnoreCase(key)) {
                    contentType = entry.getValue();
                    break;
                }
            }
            MediaType mediaType;
            if (StringUtils.isNotBlank(contentType)) {
                mediaType = MediaType.get(contentType);
                builder.post(RequestBody.create(mediaType, body)).build();
            } else {
                mediaType = DEFAULT_MEDIA_TYPE;
                String bodyJson = new String(body, StandardCharsets.UTF_8);
                builder.post(RequestBody.create(mediaType, bodyJson)).build();
            }
        }
        return builder.build();
    }

    @Deprecated
    private static OkHttpClient buildOkHttpClientWithSsl(String proxyIp, int proxyPort, String username, String password) {
        try {
            TrustManager[] trustAllCerts = buildTrustManagers();

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, buildX509TrustManager())
//                    .callTimeout(5, TimeUnit.SECONDS)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .hostnameVerifier((hostname, session) -> true)
                    .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort)));
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                clientBuilder.proxyAuthenticator((route, res) -> res.request().newBuilder().header("Proxy-Authorization",
                        Credentials.basic(username, password)).build());
            }
            return clientBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
            return new OkHttpClient.Builder().build();
        }
    }

    private static TrustManager[] buildTrustManagers() {
        return new TrustManager[]{buildX509TrustManager()};
    }

    private static X509TrustManager buildX509TrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }
        };
    }

    static void appendParams(String targetUrl, Map<String, String> params) {
        if (params != null && !params.isEmpty()) {
            StringBuilder stringBuilder = new StringBuilder();
            params.keySet().forEach(res -> {
                if (StringUtils.isNotBlank(stringBuilder)) {
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
            // 拼接参数
            targetUrl = targetUrl + stringBuilder;
        }
    }

    static void buildAuth(OkHttpClient.Builder clientBuilder, String username, String password) {
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            Authenticator authenticator = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            clientBuilder.proxyAuthenticator(authenticator);
        }
    }

    static void buildProxy(OkHttpClient.Builder clientBuilder, String proxyIp, int proxyPort) {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
        clientBuilder.proxy(proxy);
//        clientBuilder.connectTimeout(5, TimeUnit.SECONDS)
////                .callTimeout(5, TimeUnit.SECONDS)
//                .readTimeout(5, TimeUnit.SECONDS)
//                .writeTimeout(5, TimeUnit.SECONDS);
    }

    public static Response sendGetByProxy2(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                           Map<String, String> header, Map<String, String> params) throws IOException {

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(5000, TimeUnit.MILLISECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS);
        // 问号拼接参数
        appendParams(targetUrl, params);
        // auth
        buildAuth(clientBuilder, username, password);
        // proxy
        buildProxy(clientBuilder, proxyIp, proxyPort);
        // request
        Request request = buildGetRequest(targetUrl, header);
        OkHttpClient client = clientBuilder.build();
        return client.newCall(request).execute();
    }

    public static Response doGetByProxyIp(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                          int connectTimeoutSeconds, int readTimeoutSeconds, int writeTimeoutSeconds) throws IOException {
        return doGetByProxyIp(targetUrl, proxyIp, proxyPort, username, password, null, null, connectTimeoutSeconds, readTimeoutSeconds, writeTimeoutSeconds);
    }

    public static Response doGetByProxyIp(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                          Map<String, String> header, Map<String, String> params,
                                          int connectTimeoutSeconds,
                                          int readTimeoutSeconds,
                                          int writeTimeoutSeconds) throws IOException {

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS);
        // 问号拼接参数
        appendParams(targetUrl, params);
        // auth
        buildAuth(clientBuilder, username, password);
        // proxy
        buildProxy(clientBuilder, proxyIp, proxyPort);
        // request
        Request request = buildGetRequest(targetUrl, header);
        OkHttpClient client = clientBuilder.build();
        return client.newCall(request).execute();
    }

    /**
     * 使用代理ip发送get请求
     *
     * @param targetUrl 目标url
     * @param proxyIp   代理ip
     * @param proxyPort 代理端口
     * @param username  用户名
     * @param password  密码
     * @param header    请求头
     * @param params    请求参数
     * @return
     * @throws IOException
     */
    public Response sendGetByProxy(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                   Map<String, String> header, Map<String, String> params) throws IOException {

        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        if (defaultTunnel == null) {
            throw new IOException("defaultTunnel is null");
        }
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(defaultTunnel.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(defaultTunnel.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(defaultTunnel.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(false);
        // 问号拼接参数
        appendParams(targetUrl, params);
        // auth
        buildAuth(clientBuilder, username, password);
        // proxy
        buildProxy(clientBuilder, proxyIp, proxyPort);
        // request
        Request request = buildGetRequest(targetUrl, header);
        OkHttpClient client = clientBuilder.build();
        return client.newCall(request).execute();
    }

    /**
     * 使用代理ip发送get请求（带body）
     *
     * @param targetUrl 目标url
     * @param proxyIp   代理ip
     * @param proxyPort 代理端口
     * @param username  用户名
     * @param password  密码
     * @param header    请求头
     * @param params    请求参数
     * @return
     * @throws IOException
     */
    public Response sendGetByProxy(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                   Map<String, String> header, Map<String, String> params, byte[] body) throws IOException {

        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        if (defaultTunnel == null) {
            throw new IOException("defaultTunnel is null");
        }
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(defaultTunnel.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(defaultTunnel.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(defaultTunnel.getWriteTimeoutSeconds(), TimeUnit.SECONDS);
        // 问号拼接参数
        appendParams(targetUrl, params);
        // auth
        buildAuth(clientBuilder, username, password);
        // proxy
        buildProxy(clientBuilder, proxyIp, proxyPort);
        // request
        Request request = buildGetRequest(targetUrl, header);
        OkHttpClient client = clientBuilder.build();
        return client.newCall(request).execute();
    }

    /**
     * 使用代理ip发送post请求
     *
     * @param targetUrl 目标url
     * @param proxyIp   代理ip
     * @param proxyPort 代理端口
     * @param username  用户名
     * @param password  密码
     * @param header    请求头
     * @param body      请求body
     * @return
     * @throws IOException
     */
    public Response sendPostByProxy(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                    Map<String, String> header, byte[] body) throws IOException {

        TunnelInstance defaultTunnel = tunnelInitService.getDefaultTunnel();
        if (defaultTunnel == null) {
            throw new IOException("defaultTunnel is null");
        }
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(defaultTunnel.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(defaultTunnel.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(defaultTunnel.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(false);
        // auth
        buildAuth(clientBuilder, username, password);
        // proxy
        buildProxy(clientBuilder, proxyIp, proxyPort);
        // request
        Request request = buildPostRequest(targetUrl, header, body);
//        System.out.println("okhttp===============" + request.toString());
//        System.out.println("okhttp body===============" + new String(body, StandardCharsets.UTF_8));
        OkHttpClient client = clientBuilder.build();
        return client.newCall(request).execute();
    }

    /**
     * 使用代理ip发送 https get 请求
     *
     * @param targetUrl 目标url
     * @param proxyIp   代理ip
     * @param proxyPort 代理端口
     * @param username  用户名
     * @param password  密码
     * @param header    请求头
     * @param params    请求参数,用于问号拼接
     * @return
     * @throws IOException
     */
    public Response sendGetByProxyWithSsl(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                          Map<String, String> header, Map<String, String> params) throws IOException {
        // params
        appendParams(targetUrl, params);
        // request
        Request request = buildGetRequest(targetUrl, header);
        // ssl client
        OkHttpClient okHttpClient = buildOkHttpClientWithSsl(proxyIp, proxyPort, username, password);
        return okHttpClient.newCall(request).execute();
    }

    /**
     * 使用代理ip发送 https post 请求
     *
     * @param targetUrl 目标url
     * @param proxyIp   代理ip
     * @param proxyPort 代理端口
     * @param username  用户名
     * @param password  密码
     * @param header    请求头
     * @param body      请求body
     * @return
     * @throws IOException
     */
    public Response sendPostByProxyWithSsl(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                           Map<String, String> header, byte[] body) throws IOException {
        Request request = buildPostRequest(targetUrl, header, body);
        OkHttpClient okHttpClient = buildOkHttpClientWithSsl(proxyIp, proxyPort, username, password);
        return okHttpClient.newCall(request).execute();
    }

}
