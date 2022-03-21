package com.dataeye.proxy;

import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * @author jaret
 * @date 2022/3/19 17:00
 * @description 测试第三方隧道代理接口
 */
public class TestTunnelProxyWithOkHttp {

    public static void main(String args[]) throws IOException {
        testTunnelProxy();
    }

    /**
     * 测试隧道代理，访问某个地址
     */
    public static void testTunnelProxy() throws IOException {
        // 目标网站
        String targetUrl = "https://www.baidu.com";
        // 用户名密码, 若已添加白名单则不需要添加
        final String username = "t14552639449562";
        final String password = "36sphe7b";
        // 代理服务器IP
        String ip = "tps579.kdlapi.com";
        int port = 15818;

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port));

        Authenticator authenticator = (route, response) -> {
            String credential = Credentials.basic(username, password);
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy)
                .proxyAuthenticator(authenticator)
                .build();

        Request request = new Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
                .addHeader("Connection","close")
                .build();

        Response response = client.newCall(request).execute();
        System.out.println(response.body().string());
    }

    /**
     * 获取隧道代理IP
     */
    public static void getTunnelProxyIp(){
        //todo http://tps.kdlapi.com/api/gettps?orderid=964552639449340&num=1&signature=qac2esk7yf9785grsbdgfclgi3aqlmci
    }


}
