package com.dataeye.proxy;

import com.dataeye.proxy.tunnel.TunnelProxyServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.Resource;

/**
 * @author jaret
 * @date 2022/3/18 13:19
 * @description
 */
@Slf4j
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class TunnelProxyApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(TunnelProxyApplication.class, args);
    }

    @Resource
    TunnelProxyServer tunnelProxyServer;

    @Override
    public void run(String... args) throws InterruptedException {
        tunnelProxyServer.start();
        log.info("代理服务器启动成功");
    }

}