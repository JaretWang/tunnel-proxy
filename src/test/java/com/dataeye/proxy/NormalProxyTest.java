package com.dataeye.proxy;

import com.dataeye.proxy.server.ProxyServer;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author jaret
 * @date 2022/3/21 15:51
 * @description 常规代理请求测试
 */
@SpringBootTest
public class NormalProxyTest extends TestProxyBase {

//    private static final String url = "https://www.baidu.com";
//    private static final String url = "https://www.taobao.com";
    private static final String url = "https://www.zhihu.com/";
    private static final String proxyHost = "127.0.0.1";
    private static final int proxyPort = 8123;

    @Test
    public void proxy() throws Exception {
        assertThat(sendRequest(url, proxyHost, proxyPort)).isEqualTo(200);
    }

    private static int sendRequest(String url, String proxyHost, int proxyPort) throws Exception {
        HttpResponse httpResponse = Request.Get(url)
                .viaProxy(new HttpHost(proxyHost, proxyPort))
                .execute()
                .returnResponse();
        HttpEntity entity = httpResponse.getEntity();
        String content = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
//        System.out.println(content);
        return httpResponse.getStatusLine().getStatusCode();
    }

}
