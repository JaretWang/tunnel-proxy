package com.dataeye.proxy;

import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description
 */
public class TestEdxSale {

    // edx-sale
    private static final String pageUrl = "https://ec.snssdk.com/product/lubanajaxstaticitem?id=3453524332989543254&b_type_new=0";
//    private static final String proxyIp = "120.79.147.167";
    private static final String proxyIp = "127.0.0.1";
    private static final int proxyPort = 21333;
    private static final String username = "dataeye";
    private static final String password = "dataeye++123";

    public static void main(String[] args) throws IOException {
        for (int i = 0; i < 1; i++) {
            Response response = sendByOkHttp();
            String result = response.body().string();
            System.out.println(result);
        }
    }

    public static Response sendByOkHttp() throws IOException {
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
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
//                .addHeader("Connection", "Keep-Alive")
                .addHeader("Connection", "close")
                .build();

        OkHttpClient client = clientBuilder.build();

        return client.newCall(request).execute();
    }
}
