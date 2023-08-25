package com.dataeye.proxy;

import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.utils.OkHttpTool;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description 测试单个ip的并发上限
 */
public class TestIpConcurrent {

    private static final String pageUrl = "https://www.baidu.com";
//    private static final String pageUrl = "http://www.zhihu.com";
//    private static final String pageUrl = "https://www.jd.com";

    //    private static final String proxyIp = "127.0.0.1";
    private static final String proxyIp = "tunnel-proxy-1-internet.de123.net";
    private static final int proxyPort = 21331;
    private static final String username = "dataeye";
    private static final String password = "dataeye++123";

    public static void main(String[] args) throws InterruptedException, IOException {
        HashMap<java.lang.String, java.lang.String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36");
        headers.put("Connection", "close");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("wcj", "123");
        new OkHttpTool().sendPostByProxy("https://www.baidu.com", "127.0.0.1", 21332,
                "", "", headers, jsonObject.toJSONString().getBytes());


//        int totalThread = 20;
//        int totalTask = 30;
//        ExecutorService executorService = Executors.newFixedThreadPool(totalThread);
//        CountDownLatch countDownLatch = new CountDownLatch(totalTask);
//        for (int i = 0; i < totalTask; i++) {
//            executorService.submit(new TestTask(countDownLatch));
//        }
//        countDownLatch.await();
//        executorService.shutdownNow();
    }

    public static Response sendByOkHttp(boolean isHttps) throws IOException {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        if (isHttps) {
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
        }

        Request request = new Request.Builder()
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
//                .addHeader("Connection", "Keep-Alive")
                .addHeader("Connection", "close")
                .build();

        OkHttpClient client = clientBuilder.build();

        return client.newCall(request).execute();
    }

    static class TestTask implements Runnable {

        CountDownLatch countDownLatch;

        public TestTask(CountDownLatch countDownLatch) {
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            long begin = System.currentTimeMillis();
            Response response = null;
            try {
                response = sendByOkHttp(true);
            } catch (IOException e) {
                System.out.println("错误: " + e.getCause().getMessage());
            }
            long end = System.currentTimeMillis();
            int code;
            if (response == null) {
                code = 1001;
            } else {
                code = response.code();
                if (code != 200) {
                    String content = response.body().toString();
                    System.out.println(content);
                }
            }
            System.out.println("响应码: " + code + ", 耗时：" + (end - begin) + " ms");
            countDownLatch.countDown();
        }
    }

}
