package com.dataeye.proxy;

import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.tunnel.TunnelProxyServer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * @author jaret
 * @date 2022/3/25 17:26
 * @description
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@ComponentScan(basePackages = "com.dataeye.proxy")
public class TestTunnelProxy {

    @Resource
    private TunnelProxyServer tunnelProxyServer;
    @Resource
    private ProxyServerConfig proxyServerConfig;

    // 要访问的目标网页
    private static final String pageUrl = "https://www.zhihu.com";
    private static final String proxyIp = "127.0.0.1";
    private static final int proxyPort = 8123;
    private static final String targetFile = System.getProperty("user.dir") + File.separator + "proxy_result" + File.separator + "repsonse11.html";

    @Test
    public void test() throws IOException, InterruptedException {
//        new Thread(() -> tunnelProxyServer.start()).start();
//        Thread.sleep(5000);

        // JDK 8u111版本后，目标页面为HTTPS协议，启用proxy用户密码鉴权
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        CredentialsProvider credsProvider = new BasicCredentialsProvider();

        String username = proxyServerConfig.getProxyUserName();
        String password = proxyServerConfig.getProxyPassword();

        credsProvider.setCredentials(new AuthScope(proxyIp, proxyPort),
                new UsernamePasswordCredentials(username, password));
        CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        // 构造请求
        URL url = new URL(pageUrl);
        HttpHost target = new HttpHost(url.getHost(), url.getDefaultPort(), url.getProtocol());
        HttpHost proxy = new HttpHost(proxyIp, proxyPort);

        /*
        httpclient各个版本设置超时都略有不同, 此处对应版本4.5.6
        setConnectTimeout：设置连接超时时间
        setConnectionRequestTimeout：设置从connect Manager获取Connection 超时时间
        setSocketTimeout：请求获取数据的超时时间
        */
        RequestConfig config = RequestConfig.custom()
                .setProxy(proxy).setConnectTimeout(6000)
                .setConnectionRequestTimeout(2000).setSocketTimeout(6000).build();
        HttpGet httpget = new HttpGet(url.getPath());
        httpget.setConfig(config);
        httpget.addHeader("Accept-Encoding", "gzip"); // 使用gzip压缩传输数据让访问更快
        httpget.addHeader("Connection", "close");
        httpget.addHeader("Proxy-Authorization", Credentials.basic(username, password));
        httpget.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.66 Safari/537.36");
        CloseableHttpResponse response = httpclient.execute(target, httpget);

        try {
            // 返回结果写入文件
            String content = EntityUtils.toString(response.getEntity());
            FileUtils.writeStringToFile(new File(targetFile), content, StandardCharsets.UTF_8, false);
        } finally {
            // 关闭资源
            response.close();
            httpclient.close();
//            tunnelProxyServer.shutdown();
        }
    }

}
