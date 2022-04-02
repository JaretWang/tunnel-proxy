package com.dataeye.proxy;

import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.utils.ProxyUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

/**
 * @author jaret
 * @date 2022/3/28 19:36
 * @description 测试芝麻代理接口
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@ComponentScan(basePackages = "com.dataeye.proxy")
public class TestZhiMaProxy {

    @Autowired
    ProxyServerConfig proxyServerConfig;

    @Test
    public void test() throws IOException {
        String targetUrl = "https://www.baidu.com";
        String directIpAccessLink = proxyServerConfig.getDirectIpAccessLink();
        build(targetUrl, directIpAccessLink, proxyServerConfig);
        System.out.println("---------------------------------------");

//        String exclusiveIpAccessLink = proxyServerConfig.getExclusiveIpAccessLink();
//        build(targetUrl,exclusiveIpAccessLink,proxyServerConfig);
//        System.out.println("---------------------------------------");
//
//        String tunnelIpAccessLink = proxyServerConfig.getTunnelIpAccessLink();
//        build(targetUrl,tunnelIpAccessLink,proxyServerConfig);
    }

    void build(String targetUrl, String proxyAccessLink, ProxyServerConfig proxyServerConfig) throws IOException {
        String proxyUserName = proxyServerConfig.getProxyUserName();
        String proxyPassword = proxyServerConfig.getProxyPassword();

        List<String> randomProxyIpList = IpSelector.getRandomProxyIpList(proxyAccessLink);
        log.info("代理ip列表:{}", randomProxyIpList);
        System.out.println(randomProxyIpList.toString());
        String hostPort = randomProxyIpList.get(0);
        String ip = hostPort.split(":")[0];
        String port = hostPort.split(":")[1];
        String content = ProxyUtils.sendByOkHttp(targetUrl, ip, Integer.parseInt(port), proxyUserName, proxyPassword);
        log.info("请求响应内容长度:{}", content.length());
    }


}
