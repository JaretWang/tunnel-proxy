package com.dataeye.proxy.utils;

import okhttp3.*;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/3/28 19:37
 * @description
 */
public class ProxyUtils {

    /**
     * 使用 okhttp 发送代理请求
     * @param targetUrl
     * @param proxyIp
     * @param peoxyPort
     * @param username
     * @param password
     * @return
     * @throws IOException
     */
    public static String sendByOkHttp(String targetUrl, String proxyIp, int peoxyPort, String username,String password) throws IOException {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, peoxyPort));
        Authenticator authenticator = null;
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            authenticator = (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy)
                .proxyAuthenticator(Objects.requireNonNull(authenticator))
                .build();

        Request request = new Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
                .addHeader("Connection","close")
                .build();

        Response response = client.newCall(request).execute();
        return Objects.requireNonNull(response.body()).string();
    }

}
