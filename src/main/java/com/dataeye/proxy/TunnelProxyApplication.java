package com.dataeye.proxy;

import com.dataeye.proxy.apn.ApnProxyServer;
import com.dataeye.proxy.apn.config.ApnProxyConfigReader;
import com.dataeye.proxy.apn.config.ApnProxyRemoteRulesConfigReader;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.impl.TunnelDistributeServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

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

//    @Autowired
//    TunnelDistributeServiceImpl tunnelDistributeService;
//    @Resource
//    TunnelInitMapper tunnelInitMapper;
//    @Resource
//    ApnProxyServer apnProxyServer;
//
////    @PostConstruct
//    public void start() {
//        log.info("开启apn代理服务器");
//
//        ApnProxyConfigReader apnProxyConfigReader = new ApnProxyConfigReader();
//        apnProxyConfigReader.read(TunnelProxyApplication.class
//                .getResourceAsStream("/plain-proxy-config.xml"));
//
//        ApnProxyRemoteRulesConfigReader apnProxyRemoteRulesConfigReader = new ApnProxyRemoteRulesConfigReader();
//        apnProxyRemoteRulesConfigReader.read(TunnelProxyApplication.class
//                .getResourceAsStream("/plain-proxy-config.xml"));
//
////        apnProxyServer.start();
//    }
//
//
////    @PostConstruct
//    public void test2() throws IOException {
//        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
//        for (TunnelInstance tunnelInstance : tunnelInstances) {
//            TunnelAllocateResult allocateResult = tunnelDistributeService.getDistributeParams(null, tunnelInstance);
//            ApnProxyRemoteChooser.PROXY_IP = allocateResult.getIp();
//            ApnProxyRemoteChooser.PROXY_PORT = allocateResult.getPort();
//            ApnProxyRemoteChooser.USERNAME = allocateResult.getUsername();
//            ApnProxyRemoteChooser.PASSWORD = allocateResult.getPassword();
//            log.info("IP 分配结果：{}", allocateResult.toString());
//            if (allocateResult != null) {
//                break;
//            }
//        }
//    }

}
