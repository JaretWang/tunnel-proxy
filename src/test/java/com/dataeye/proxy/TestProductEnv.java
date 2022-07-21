package com.dataeye.proxy;

import com.dataeye.proxy.utils.OkHttpTool;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

/**
 * @author jaret
 * @date 2022/3/28 14:21
 * @description
 */
@Slf4j
@SpringBootTest(classes = TunnelProxyApplication.class)
public class TestProductEnv {

    private static final String pageUrl = "https://www.baidu.com";
    private static final String proxyIp = "127.0.0.1";
    private static final int proxyPort = 21332;
    private static final String username = "dataeye";
    private static final String password = "dataeye++123";
    @Autowired
    OkHttpTool okHttpTool;

    @Test
    public void test() throws IOException {
        Response response = okHttpTool.sendGetByProxy(pageUrl, proxyIp, proxyPort, username, password, null, null);
        System.out.println(response.body().string());
        response.close();
    }

}
