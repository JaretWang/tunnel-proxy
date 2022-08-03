package com.dataeye.proxy.rola;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;

/**
 * @author jaret
 * @date 2022/8/2 17:51
 * @description
 */
public class AutProxyJava {

    public static void main(String[] args) throws IOException {
        testWithOkHttp();
//        testSocks5WithOkHttp();
    }

    public static void testWithOkHttp() throws IOException {
//        String url = "http://ip123.in/ip.json";
        String url = "https://www.baidu.com";
//        String gateIp = "gate.rola.info";
//        int gatePort = 1000;
        String gateIp = "gate9.rola.info";
        int gatePort = 1031;
        String username = "dataeye_1";
        String password = "DataEye123$%^";
        OkHttpClient client = new OkHttpClient().newBuilder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(gateIp, gatePort)))
                .proxyAuthenticator((route, response) -> response.request().newBuilder()
                .header("Proxy-Authorization", Credentials.basic(username, password))
                .build()).build();
        Request request = new Request.Builder().url(url).build();
        okhttp3.Response response = client.newCall(request).execute();
        String responseString = response.body().string();
        System.out.println(responseString);
    }

    public static void testSocks5WithOkHttp() throws IOException {
        String url = "http://ip123.in/ip.json";
        String gateIp = "gate.rola.info";
        int gatePort = 2000;
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(gateIp, gatePort));
        java.net.Authenticator.setDefault(new java.net.Authenticator() {
            private PasswordAuthentication authentication =
                    new PasswordAuthentication("账户", "密码".toCharArray());

            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return authentication;
            }
        });
        OkHttpClient client = new OkHttpClient().newBuilder().proxy(proxy).build();

        Request request = new Request.Builder().url(url).build();
        okhttp3.Response response = client.newCall(request).execute();
        String responseString = response.body().string();
        System.out.println(responseString);
    }

}

