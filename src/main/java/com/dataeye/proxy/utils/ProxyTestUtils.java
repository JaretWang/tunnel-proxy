package com.dataeye.proxy.utils;

import okhttp3.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/4/21 10:53
 * @description
 */
public class ProxyTestUtils {

    private static final Logger log = MyLogbackRollingFileUtil.getLogger("ProxyTestUtils");

    /**
     * 使用代理ip发送，获取响应
     *
     * @param targetUrl 目标网页
     * @param proxyIp   代理ip
     * @param proxyPort 代理端口
     * @param username  用户名
     * @param password  密码
     * @return
     * @throws IOException
     */
    public static String sendByProxyIp(String targetUrl, String proxyIp, int proxyPort, String username, String password) throws IOException {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
        Authenticator authenticator = (route, response) -> {
            String credential = Credentials.basic(username, password);
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
        clientBuilder.proxy(proxy);
        clientBuilder.connectTimeout(60, TimeUnit.SECONDS);
        clientBuilder.callTimeout(60, TimeUnit.SECONDS);
        clientBuilder.proxyAuthenticator(authenticator);

        Request request = new Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
                .addHeader("Connection", "Keep-Alive")
                .build();

        OkHttpClient client = clientBuilder.build();

        Response response = client.newCall(request).execute();
        return Objects.requireNonNull(response.body()).string();
    }

}
