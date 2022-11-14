package com.dataeye.proxy.vps;

import com.dataeye.proxy.TunnelProxyApplication;
import com.dataeye.proxy.service.impl.VpsFetchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author jaret
 * @date 2022/11/8 18:34
 * @description
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TunnelProxyApplication.class)
public class VpsTest {

    @Autowired
    VpsFetchServiceImpl vpsFetchService;

    @Test
    public void test1() {
        vpsFetchService.scheduleGetAllVpsIp();
    }

}
