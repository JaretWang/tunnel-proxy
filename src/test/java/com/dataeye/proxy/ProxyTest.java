package com.dataeye.proxy;

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.service.ProxyService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

/**
 * @author jaret
 * @date 2022/4/1 16:31
 * @description
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@ComponentScan(basePackages = "com.dataeye.proxy")
public class ProxyTest {

    @Autowired
    private ProxyService proxyService;

    @Test
    public void test(){
        Optional<ProxyCfg> one = proxyService.getOne();
        ProxyCfg proxyCfg = one.get();
        System.out.println(proxyCfg);
    }

}
