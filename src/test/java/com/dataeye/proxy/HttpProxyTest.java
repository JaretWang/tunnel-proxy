package com.dataeye.proxy;

import com.dataeye.proxy.config.ProxyServerConfig;
import okhttp3.Credentials;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class HttpProxyTest {

    @Resource
    private ProxyServerConfig proxyServerConfig;
    private static final String url = "https://www.baidu.com";
    private static final String proxyHost = "tps579.kdlapi.com";
    private static final int proxyPort = 15818;

    @Test
    public void proxy() throws Exception {
        assertThat(statusCodeOfRequest(url)).isEqualTo(200);
    }

    public static void main(String[] args) throws Exception {
//        doProxyGetWithHttpClient();
//        testProxyWithHttp(url);
        assertThat(statusCodeOfRequest2(url)).isEqualTo(200);
    }

    private int statusCodeOfRequest(String url) throws Exception {
        HttpResponse httpResponse = Request.Get(url)
                .viaProxy(new HttpHost(proxyServerConfig.getHost(), proxyServerConfig.getPort()))
                .execute()
                .returnResponse();
        HttpEntity entity = httpResponse.getEntity();
        String content = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
        System.out.println(content);
        return httpResponse.getStatusLine().getStatusCode();
    }

    private static int statusCodeOfRequest2(String url) throws Exception {
        HttpResponse httpResponse = Request.Get(url)
                .viaProxy(new HttpHost("127.0.0.1", 8123))
                .execute()
                .returnResponse();
        HttpEntity entity = httpResponse.getEntity();
        String content = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
        System.out.println(content);
        return httpResponse.getStatusLine().getStatusCode();
    }

    private static int testProxyWithHttp(String url) throws Exception {

        final String username = "t14552639449562";
        final String password = "36sphe7b";
        String credential = Credentials.basic(username, password);
        HttpResponse httpResponse = Request.Get(url)
                .viaProxy(new HttpHost(proxyHost, proxyPort))
                .setHeader("Proxy-Authorization", credential)
                .execute()
                .returnResponse();
        HttpEntity entity = httpResponse.getEntity();
        String content = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
        System.out.println(content);
        return httpResponse.getStatusLine().getStatusCode();
    }


    public static void doProxyGetWithHttpClient() {
        StringBuffer sb = new StringBuffer();
        //创建HttpClient实例
        HttpClient client = getHttpClient();
        //创建httpGet
        HttpGet httpGet = new HttpGet(url);
        //执行
        try {
            HttpResponse response = client.execute(httpGet);
            HttpEntity entry = response.getEntity();
            if (entry != null) {
                InputStreamReader is = new InputStreamReader(entry.getContent());
                BufferedReader br = new BufferedReader(is);
                String str = null;
                while ((str = br.readLine()) != null) {
                    sb.append(str.trim());
                }
                br.close();
            }

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println(sb.toString());

    }

    /**
     * 设置代理
     *
     * @return
     */
    public static HttpClient getHttpClient() {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        httpClient.getParams().setParameter(ConnRouteParams.DEFAULT_PROXY, proxy);
        return httpClient;
    }

}