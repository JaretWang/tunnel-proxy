package com.dataeye.proxy;

import com.dataeye.proxy.server.ProxyServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author jaret
 * @date 2022/3/21 17:17
 * @description
 */
@Slf4j
public class TestProxyBase {

    @Autowired
    private ProxyServer server;

    @BeforeClass
    public void setUpServer() {
        Thread t = new Thread(() -> {
            log.info("开启代理服务器");
            server = new ProxyServer();
            server.start();
            log.info("开启完成");
        });

        t.start();
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterClass
    public void shutDownServer() {
        log.info("关闭代理服务器");
        server.shutdown();
    }
}
