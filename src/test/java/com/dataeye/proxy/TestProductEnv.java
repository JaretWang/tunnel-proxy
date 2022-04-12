package com.dataeye.proxy;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description
 */
@Slf4j
public class TestProductEnv {

    private static final String pageUrl = "https://www.baidu.com";
//    private static final String pageUrl = "http://www.taobao.com";

    private static final String proxyIp = "tunnel-proxy-1-internet.de123.net";
    private static final int proxyPort = 21332;
    private static final String username = "dataeye";
    private static final String password = "dataeye++123";

    public static void main(String[] args) throws IOException {
        long begin = System.currentTimeMillis();
        System.out.println(sendByOkHttp());
        long end = System.currentTimeMillis();
        log.warn("耗时：{} ms", end - begin);
    }

    public static String sendByOkHttp() throws IOException {
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
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
                .addHeader("Connection", "Keep-Alive")
                .build();

        OkHttpClient client = clientBuilder.build();

        Response response = client.newCall(request).execute();
        log.info("响应状态码：{}", response.code());
        return Objects.requireNonNull(response.body()).string();
    }

}
