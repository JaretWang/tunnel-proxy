package com.dataeye.proxy.utils;

import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/4/20 14:50
 * @description
 */
public class OkHttpTool {

    private static final OkHttpClient OKHTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
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
            header.forEach(builder::addHeader);
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
    public static String doGet(String reqUrl, Map<String, String> header, Map<String, String> params, boolean flag) {
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
        Request.Builder builder = new Request.Builder()
                .url(requestUrl);
        if (header != null && !header.isEmpty()) {
            header.forEach(builder::addHeader);
        }
        Request request = builder.get().build();
        try {
            Response response = OKHTTP_CLIENT.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("HTTP GET同步请求失败 URL:" + reqUrl, e);
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
        Request.Builder builder = new Request.Builder()
                .url(requestUrl);
        Request request = builder.get().build();
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
    public static Response sendByProxy(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                       Map<String, String> params, JSONObject body) throws IOException {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            Authenticator authenticator = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            clientBuilder.proxyAuthenticator(authenticator);
        }

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
    public static Response sendGetByProxy(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                          Map<String, String> header, Map<String, String> params) throws IOException {
        //处理参数
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

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            Authenticator authenticator = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            clientBuilder.proxyAuthenticator(authenticator);
        }

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
        clientBuilder.proxy(proxy);
        clientBuilder.connectTimeout(5, TimeUnit.SECONDS)
//                .callTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS);

        Request.Builder builder = new Request.Builder()
                .url(targetUrl);

        if (header != null && !header.isEmpty()) {
            header.forEach(builder::addHeader);
        }
        Request request = builder.get().build();
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
    public static Response sendPostByProxy(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                           Map<String, String> header, JSONObject body) throws IOException {

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            Authenticator authenticator = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            clientBuilder.proxyAuthenticator(authenticator);
        }
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
        clientBuilder.proxy(proxy);
        clientBuilder.connectTimeout(5, TimeUnit.SECONDS)
//                .callTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS);

        Request.Builder builder = new Request.Builder().url(targetUrl);
        if (header != null && !header.isEmpty()) {
            header.forEach(builder::addHeader);
        }

        Request request;
        if (body != null && !body.isEmpty()) {
            RequestBody requestBody = RequestBody.create(MEDIA_TYPE, body.toString());
            request = builder.post(requestBody).build();
        } else {
            request = builder.build();
        }
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
    public static Response sendGetByProxyWithSsl(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                                 Map<String, String> header, Map<String, String> params) throws IOException {
        //处理参数
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
        Request request = buildGetRequest(targetUrl, header);
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
    public static Response sendPostByProxyWithSsl(String targetUrl, String proxyIp, int proxyPort, String username, String password,
                                                  Map<String, String> header, JSONObject body) throws IOException {
        Request request = buildPostRequest(targetUrl, header, body);
        OkHttpClient okHttpClient = buildOkHttpClientWithSsl(proxyIp, proxyPort, username, password);
        return okHttpClient.newCall(request).execute();
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

    private static Request buildPostRequest(String url, Map<String, String> headers, JSONObject body) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get();
        if (headers != null && !headers.isEmpty()) {
            builder.headers(Headers.of(headers));
        }
        if (body != null && !body.isEmpty()) {
            builder.post(RequestBody.create(body.toJSONString(), MEDIA_TYPE));
        }
        return builder.build();
    }

    private static OkHttpClient buildOkHttpClientWithSsl(String proxyIp, int proxyPort, String username, String password) {
        try {
            TrustManager[] trustAllCerts = buildTrustManagers();

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, buildX509TrustManager())
                    .callTimeout(5, TimeUnit.SECONDS)
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

    public static void main(String[] args) throws IOException {
        String url = "https://www.baidu.com/s?wd=%E3%80%8A%E6%96%B0%E9%97%BB%E8%81%94%E6%92%AD%E3%80%8B%E6%8A%AB%E9%9C%B2%E9%98%B2%E7%96%AB%E9%87%8D%E7%A3%85%E4%BF%A1%E5%8F%B7&sa=fyb_n_homepage&rsv_dl=fyb_n_homepage&from=super&cl=3&tn=baidutop10&fr=top1000&rsv_idx=2&hisfilter=1";
        HashMap<String, String> headers = new HashMap<String, String>() {{
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36");
            put("Connection", "close");
        }};
        // "106.42.50.223:4231(true, 2022-05-07T12:14:44)"
//        Response response = sendGetByProxyWithSsl(url, "106.42.50.223", 4231, "", "", headers,null);
        Response response = sendPostByProxyWithSsl(url, "106.42.50.223", 4231, "", "", headers,null);
        System.out.println(Objects.requireNonNull(response.body()).string());
        closeResponse(response);
    }

}
