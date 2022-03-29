package com.dataeye.proxy;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description
 */
@Slf4j
public class TestProductEnviroment {

    private static final String pageUrl = "https://www.zhihu.com";
    private static final String proxyIp = "tunnel-proxy-1-internet.de123.net";
    private static final int proxyPort = 21330;
    private static final String username = "t14480740933876";
    private static final String password = "wnwx5oeo";
    private static final String root = System.getProperty("user.dir") + File.separator + "proxy_result";

    public static void main(String[] args) throws IOException {
        sendByHttpClient("HttpClient_repsonse.html");
//        sendByOkHttp("OkHttp_repsonse.html");
//        sendByOriginal("Original_repsonse.html");
    }

    public static void sendByHttpClient(String saveFileName) throws IOException {
        String saveFile = root + File.separator + saveFileName;

        // JDK 8u111版本后，目标页面为HTTPS协议，启用proxy用户密码鉴权
        CredentialsProvider credsProvider = new BasicCredentialsProvider();

        credsProvider.setCredentials(new AuthScope(proxyIp, proxyPort), new UsernamePasswordCredentials(username, password));
        CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        // 构造请求
        URL url = new URL(pageUrl);
        HttpHost target = new HttpHost(url.getHost(), url.getDefaultPort(), url.getProtocol());
        HttpHost proxy = new HttpHost(proxyIp, proxyPort);

        RequestConfig config = RequestConfig.custom()
                .setProxy(proxy).setConnectTimeout(6000)
                .setConnectionRequestTimeout(2000).setSocketTimeout(6000).build();

        HttpGet httpget = new HttpGet(url.getPath());
        httpget.setConfig(config);
        httpget.addHeader("Accept-Encoding", "gzip"); // 使用gzip压缩传输数据让访问更快
        httpget.addHeader("Connection", "K");
        httpget.addHeader("Proxy-Authorization", Credentials.basic(username, password));
        httpget.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.66 Safari/537.36");

        CloseableHttpResponse response = httpclient.execute(target, httpget);

        try {
            // 返回结果写入文件
            String content = EntityUtils.toString(response.getEntity());
            FileUtils.writeStringToFile(new File(saveFile), content, StandardCharsets.UTF_8, false);
        } finally {
            // 关闭资源
            response.close();
            httpclient.close();
        }
    }

    public static void sendByOkHttp(String saveFileName) throws IOException {
        String saveFile = root + File.separator + saveFileName;

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));

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
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36")
                .addHeader("Connection","close")
                .build();

        Response response = client.newCall(request).execute();
        String content = Objects.requireNonNull(response.body()).string();
        FileUtils.writeStringToFile(new File(saveFile), content, StandardCharsets.UTF_8, false);
    }

    public static void sendByOriginal(String saveFileName) {
        String saveFile = root + File.separator + saveFileName;

        HttpURLConnection httpConn = null;
        PrintWriter out = null;
        OutputStreamWriter out1 = null;
        BufferedReader in = null;
        String result = "";
        BufferedReader reader = null;
        try {
            URL urlClient = new URL(pageUrl);
            System.out.println("请求的URL========：" + urlClient);
            // 创建代理
            Proxy proxy1 = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
            // 设置代理
            httpConn = (HttpURLConnection) urlClient.openConnection(proxy1);
            // 设置通用的请求属性
            httpConn.setRequestProperty("accept", "*/*");
            httpConn.setRequestProperty("connection", "Keep-Alive");
            httpConn.setRequestProperty("Proxy-Authorization", Credentials.basic(username, password));
            httpConn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            // 发送POST请求必须设置如下两行
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            // 获取URLConnection对象对应的输出流
            //使请求报文不中文乱码
            out1 = new OutputStreamWriter(httpConn.getOutputStream(), "utf-8");
            // 发送请求参数
            // out.print(param);
            // flush输出流的缓冲
            out1.flush();
            // 定义BufferedReader输入流来读取URL的响应
            // in = new BufferedReader(
            // new InputStreamReader(httpConn.getInputStream()));

            //使返回的报文不中文乱码
            in = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), "utf-8"));

            String line;


            while ((line = in.readLine()) != null) {
                result += line;
            }
            // 断开连接
            httpConn.disconnect();
            FileUtils.writeStringToFile(new File(saveFile), result, StandardCharsets.UTF_8, false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (out1 != null) {
                    out1.close();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
