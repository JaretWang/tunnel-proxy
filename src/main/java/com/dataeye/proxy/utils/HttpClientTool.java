package com.dataeye.proxy.utils;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jaret
 * @date 2022/5/24 14:10
 * @description
 */
@Data
@Builder
public class HttpClientTool {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("TestIpConcurrent");

    private String url;
    private String proxyIp;
    private int proxyPort;
    private String username;
    private String password;

//    public static void main(String[] args) {
//        String basic = Credentials.basic("username", "password");
//        String str = "username:password";
//        System.out.println(basic);
//        String basic2 = "Basic " + Base64.getEncoder().encodeToString(str.getBytes());
//        System.out.println(basic2);
//        System.out.println(basic.equals(basic2));
//    }
//
//    /**
//     * 使用jdk发送代理请求
//     */
//    public static void sendByOriginal(String targetUrl, String proxyIp, int proxyPort, String username, String password, boolean isLongConn) {
//        HttpURLConnection httpConn;
//        PrintWriter out = null;
//        OutputStreamWriter out1 = null;
//        BufferedReader in = null;
//        StringBuilder result = new StringBuilder();
//        BufferedReader reader = null;
//        try {
//            URL urlClient = new URL(targetUrl);
//            // 创建代理
//            Proxy proxy1 = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
//            // 设置代理
//            httpConn = (HttpURLConnection) urlClient.openConnection(proxy1);
//            // 设置通用的请求属性
//            httpConn.setRequestProperty("accept", "*/*");
//            if (isLongConn) {
//                httpConn.setRequestProperty("connection", "Keep-Alive");
//            } else {
//                httpConn.setRequestProperty("connection", "close");
//            }
//            String secret = username + ":" + password;
//            String credentials = "Basic " + Base64.getEncoder().encodeToString(secret.getBytes());
//            httpConn.setRequestProperty("Proxy-Authorization", credentials);
//            httpConn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
//            // 发送POST请求必须设置如下两行
//            httpConn.setDoOutput(true);
//            httpConn.setDoInput(true);
//            // 获取URLConnection对象对应的输出流, 使请求报文不中文乱码
//            out1 = new OutputStreamWriter(httpConn.getOutputStream(), StandardCharsets.UTF_8);
//            // 发送请求参数
//            // out.print(param);
//            // flush输出流的缓冲
//            out1.flush();
//            // 定义BufferedReader输入流来读取URL的响应
//            // in = new BufferedReader(
//            // new InputStreamReader(httpConn.getInputStream()));
//            //使返回的报文不中文乱码
//            in = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), StandardCharsets.UTF_8));
//
//            String line;
//            while ((line = in.readLine()) != null) {
//                result.append(line);
//            }
//            // 断开连接
//            httpConn.disconnect();
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            try {
//                if (reader != null) {
//                    reader.close();
//                }
//                if (in != null) {
//                    in.close();
//                }
//                if (out1 != null) {
//                    out1.close();
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    /**
//     * 使用 httpclient 发送代理请求
//     *
//     * @param targetUrl
//     * @param proxyIp
//     * @param proxyPort
//     * @param username
//     * @param password
//     * @param isLongConn
//     * @return
//     * @throws IOException
//     */
//    public static String sendByHttpClient(String targetUrl, String proxyIp, int proxyPort, String username, String password, boolean isLongConn) throws IOException {
//        // config
//        URL url = new URL(targetUrl);
//        RequestConfig.Builder configBuilder = RequestConfig.custom()
//                .setConnectTimeout(5000)
//                .setConnectionRequestTimeout(5000)
//                .setSocketTimeout(5000);
//
//        // 构造请求
//        HttpGet httpget = new HttpGet(url.getPath());
//        // header
//        // 使用gzip压缩传输数据让访问更快
//        httpget.addHeader("Accept-Encoding", "gzip");
//        if (isLongConn) {
//            httpget.addHeader("Connection", "Keep-Alive");
//        } else {
//            httpget.addHeader("Connection", "close");
//        }
//        // JDK 8u111版本后，目标页面为HTTPS协议，启用proxy用户密码鉴权
//        HttpClientBuilder clientBuilder = HttpClients.custom();
//        CredentialsProvider credsProvider = new BasicCredentialsProvider();
//        credsProvider.setCredentials(new AuthScope(proxyIp, proxyPort), new UsernamePasswordCredentials(username, password));
//        clientBuilder.setDefaultCredentialsProvider(credsProvider);
//        configBuilder.setProxy(new HttpHost(proxyIp, proxyPort));
//        httpget.addHeader("Proxy-Authorization", Credentials.basic(username, password));
//
//        // set config
//        RequestConfig config = configBuilder.build();
//        httpget.setConfig(config);
//
//        // send
//        CloseableHttpResponse response = null;
//        CloseableHttpClient httpclient = null;
//        try {
//            httpclient = clientBuilder.build();
//            HttpHost target = new HttpHost(url.getHost(), url.getDefaultPort(), url.getProtocol());
//            response = httpclient.execute(target, httpget);
//            return EntityUtils.toString(response.getEntity());
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            // 关闭资源
//            if (response != null) {
//                response.close();
//            }
//            if (httpclient != null) {
//                httpclient.close();
//            }
//        }
//        return null;
//    }

    public static void main(String[] args) {
        String url1 = "https://www.baidu.com";
        String url2 = "https://www.baidu.com";
        // "113.239.155.228:4278(true, 2022-05-26T13:20:31)
        String ip = "113.239.155.228";
        int port = 4278;
        HashMap<String, String> headers = new HashMap<String, String>(2) {{
//        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3100.0 Safari/537.36");
            put("Connection", "close");
        }};
//        String reponse1 = sendHttpProxyByJdk("GET", url1, "", ip, port, "","", headers);
//        System.out.println(reponse1);
        String reponse2 = sendHttpsProxyByJdk("GET", url2, "", ip, port, "","", headers);
        System.out.println(reponse2);
    }

    public static String sendHttpProxyByJdk(String method, String url, String param, String proxy, int port,
                                            String username, String password,
                                            Map<String, String> headers) {
        HttpURLConnection httpConn = null;
        PrintWriter out = null;
        OutputStreamWriter out1 = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        BufferedReader reader = null;
        try {
            URL urlClient = new URL(url);
            // 创建代理
            Proxy proxy1 = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy, port));
            // 设置代理
            httpConn = (HttpURLConnection) urlClient.openConnection(proxy1);
            // 设置通用的请求属性
            httpConn.setRequestMethod(method);
            // 如果正向代理设置用户名和密码,需对header进行设置
            if (username != null && !"".equals(username) && password != null && !"".equals(password)) {
                String secret = username + ":" + password;
                String credentials = "Basic " + Base64.getEncoder().encodeToString(secret.getBytes());
                httpConn.setRequestProperty("Proxy-Authorization", credentials);
            }
            // headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                httpConn.setRequestProperty(key, value);
            }

            // 发送POST请求必须设置如下两行
//            if ("post".equalsIgnoreCase(method)) {
//                httpConn.setDoOutput(true);
//                httpConn.setDoInput(true);
//            }

            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);

            // 获取URLConnection对象对应的输出流, 使请求报文不中文乱码
            out1 = new OutputStreamWriter(httpConn.getOutputStream(), "utf-8");
            out1.write(param);

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
                result.append(line);
            }
            // 断开连接
            httpConn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (in != null) {
                    in.close();
                }
                if (out1 != null) {
                    out1.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result.toString();
    }

    public static String sendHttpsProxyByJdk(String method, String url, String param, String proxy, int port,
                                             String username, String password,
                                             Map<String, String> headers) {
        HttpsURLConnection httpsConn = null;
        PrintWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        BufferedReader reader = null;
        try {
            URL urlClient = new URL(url);

            // 指定信任https
            SSLContext ssl = SSLContext.getInstance("SSL");
            ssl.init(null, new TrustManager[]{new TrustAnyTrustManager()}, new java.security.SecureRandom());
            // 创建代理虽然是https也是 Proxy.Type.HTTP
            Proxy proxy1 = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy, port));
            // 设置代理信息
            httpsConn = (HttpsURLConnection) urlClient.openConnection(proxy1);
            httpsConn.setSSLSocketFactory(ssl.getSocketFactory());
            httpsConn.setHostnameVerifier(new TrustAnyHostnameVerifier());

            // 设置通用的请求属性
            httpsConn.setRequestMethod(method);
            // headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                httpsConn.setRequestProperty(key, value);
            }
            if (username != null && !"".equals(username) && password != null && !"".equals(password)) {
                String secret = username + ":" + password;
                String credentials = "Basic " + Base64.getEncoder().encodeToString(secret.getBytes());
                httpsConn.setRequestProperty("Proxy-Authorization", credentials);
            }

            // 发送POST请求必须设置如下两行
            httpsConn.setDoOutput(true);
            httpsConn.setDoInput(true);

            if (!"get".equalsIgnoreCase(method)) {
                // 获取URLConnection对象对应的输出流
                out = new PrintWriter(httpsConn.getOutputStream());
                // 发送请求参数
                out.print(param);
                // flush输出流的缓冲
                out.flush();
            }

            // 定义BufferedReader输入流来读取URL的响应
            in = new BufferedReader(new InputStreamReader(httpsConn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result.append(line);
            }

            // 断开连接
            httpsConn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (out != null) {
                out.close();
            }
        }
        return result.toString();
    }

    private static class TrustAnyTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }

    }

    private static class TrustAnyHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }


}
