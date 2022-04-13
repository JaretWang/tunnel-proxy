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
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description
 */
@Slf4j
public class TestLocalEnviroment {

    private static final String pageUrl = "https://www.baidu.com";
//    private static final String pageUrl = "http://www.zhihu.com";
//    private static final String pageUrl = "https://www.jd.com";

    private static final String proxyIp = "127.0.0.1";
//    private static final String proxyIp = "tunnel-proxy-1-internet.de123.net";
    private static final int proxyPort = 21331;
//    private static final int proxyPort = 21332;
    private static final String username = "dataeye";
    private static final String password = "dataeye++123";

//    private static final String proxyIp = "tps582.kdlapi.com";
//    private static final int proxyPort = 15818;
//    private static final String username = "t14480740933876";
//    private static final String password = "wnwx5oeo";

    private static final String root = System.getProperty("user.dir") + File.separator + "proxy_result";

    public static void main(String[] args) throws IOException {
        long begin = System.currentTimeMillis();
//        sendByHttpClient(proxyPort+"_sendHttpsByHttpClient_repsonse.html",true);
        sendByOkHttp(proxyPort + "_sendByOkHttp_repsonse.html", true);
//        sendByOriginal("sendByOriginal_repsonse.html");
        long end = System.currentTimeMillis();
        log.warn("耗时：{} ms", end - begin);
    }

    public static void sendByHttpClient(String saveFileName, boolean isHttps) throws IOException {
        String saveFile = root + File.separator + saveFileName;

        HttpClientBuilder clientBuilder = HttpClients.custom();

        // config
        URL url = new URL(pageUrl);
        HttpHost target = new HttpHost(url.getHost(), url.getDefaultPort(), url.getProtocol());
        RequestConfig.Builder configBuilder = RequestConfig.custom().setConnectTimeout(6000).setConnectionRequestTimeout(2000).setSocketTimeout(6000);

        // 构造请求
        HttpGet httpget = new HttpGet(url.getPath());
        httpget.addHeader("Accept-Encoding", "gzip"); // 使用gzip压缩传输数据让访问更快
        httpget.addHeader("Connection", "close");
        httpget.addHeader("my-proxy-type", "direct");
        httpget.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.66 Safari/537.36");

        if (isHttps) {
            // JDK 8u111版本后，目标页面为HTTPS协议，启用proxy用户密码鉴权
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(proxyIp, proxyPort), new UsernamePasswordCredentials(username, password));
            clientBuilder.setDefaultCredentialsProvider(credsProvider);
            configBuilder.setProxy(new HttpHost(proxyIp, proxyPort));
            httpget.addHeader("Proxy-Authorization", Credentials.basic(username, password));
        }

        RequestConfig config = configBuilder.build();
        httpget.setConfig(config);
        CloseableHttpClient httpclient = clientBuilder.build();
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

    public static void sendByOkHttp(String saveFileName, boolean isHttps) throws IOException {
        String saveFile = root + File.separator + saveFileName;
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        if (isHttps) {
//            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("114.99.23.249",4213));
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
//                .addHeader("Connection", "close")
                .build();

        OkHttpClient client = clientBuilder.build();

        Response response = client.newCall(request).execute();
        log.info("响应状态码：{}", response.code());
        String content = Objects.requireNonNull(response.body()).string();
        System.out.println(content);
//        FileUtils.writeStringToFile(new File(saveFile), content, StandardCharsets.UTF_8, false);
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
            log.debug("请求的URL========：" + urlClient);
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
