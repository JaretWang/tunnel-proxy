package com.dataeye.proxy;

import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description
 */
public class TestLocalEnvNoProxy {

    private static final String pageUrl = "https://www.taobao.com";
//    private static final String pageUrl = "https://www.taobao.com";
//    private static final String proxyIp = "tunnel-proxy-1-internet.de123.net";
    private static final String proxyIp = "127.0.0.1";
    private static final int proxyPort = 21331;
//    private static final int proxyPort = 8989;
    private static final String username = "dataeye";
    private static final String password = "dataeye++123";
    private static final int totalNum = 1;
    private static final AtomicLong ok = new AtomicLong(0);
    private static final AtomicLong error = new AtomicLong(0);
    private static final CountDownLatch countDownLatch = new CountDownLatch(totalNum);

    public static void main(String[] args) throws InterruptedException {
        AtomicLong total = new AtomicLong(0);
        ExecutorService executorService = Executors.newFixedThreadPool(totalNum);
        for (int i = 0; i < totalNum; i++) {
            total.incrementAndGet();
            executorService.submit(() -> {
                try {
                    sendByOkHttp(ok, error, total, countDownLatch);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        countDownLatch.await();
        executorService.shutdown();
        System.out.println("total：" + total + ", ok: " + ok + ", error: " + error);
    }

    public static int sendByOkHttp(AtomicLong ok, AtomicLong error, AtomicLong total, CountDownLatch countDownLatch) throws IOException {
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

        Response response = client.newCall(request).execute();
        int code = response.code();
        if (code == 200) {
            ok.incrementAndGet();
        } else {
            error.incrementAndGet();
        }
        countDownLatch.countDown();
        System.out.println("total：" + total + ", ok: " + ok + ", error: " + error);
        return code;
    }

}
