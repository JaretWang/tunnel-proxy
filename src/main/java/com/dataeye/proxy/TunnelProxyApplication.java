package com.dataeye.proxy;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author jaret
 * @date 2022/3/18 13:19
 * @description
 */
@Slf4j
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties
@SpringBootApplication
@MapperScan("com.dataeye.proxy.dao")
public class TunnelProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(TunnelProxyApplication.class, args);
    }

}