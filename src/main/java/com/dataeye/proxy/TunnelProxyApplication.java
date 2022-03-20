package com.dataeye.proxy;

import com.dataeye.proxy.server.ProxyServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import javax.annotation.Resource;

/**
 * @author jaret
 * @date 2022/3/18 13:19
 * @description
 */
@EnableConfigurationProperties
@SpringBootApplication
public class TunnelProxyApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(TunnelProxyApplication.class, args);
    }

    @Resource
    ProxyServer proxyServer;

    @Override
    public void run(String... args) throws Exception {
        proxyServer.start2();
    }

}