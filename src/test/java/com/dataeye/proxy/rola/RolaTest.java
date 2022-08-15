package com.dataeye.proxy.rola;

import com.dataeye.proxy.TunnelProxyApplication;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.RolaConfig;
import com.dataeye.proxy.overseas.RolaInitService;
import com.dataeye.proxy.overseas.impl.DynamicHomeIpServiceImpl;
import com.dataeye.proxy.overseas.impl.DynamicMachineRoomIpServiceImpl;
import com.dataeye.proxy.overseas.impl.StaticMachineRoomIpServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

/**
 * @author jaret
 * @date 2022/8/11 18:22
 * @description
 */
@SpringBootTest(classes = TunnelProxyApplication.class)
public class RolaTest {

    @Autowired
    RolaConfig rolaConfig;
    @Autowired
    DynamicHomeIpServiceImpl dhi;
    @Autowired
    DynamicMachineRoomIpServiceImpl dmri;
    @Autowired
    IpSelector ipSelector;
    @Autowired
    RolaInitService rolaInitService;
    @Autowired
    StaticMachineRoomIpServiceImpl smri;

    @Test
    public void test() throws IOException {
//        smri.addIpWhiteList(rolaConfig, "116.24.66.219","办公室-wcj2");
//        smri.getOrderKey(rolaConfig);

//        List<RolaStaticIp> staticIpList = smri.getStaticIpList(rolaConfig);
//        System.out.println(JSON.toJSONString(staticIpList));

//        dhi.initIpPool();
//        dmri.initIpPool();
//        smri.initIpPool();
//        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipSelector.getProxyIpPool();
//        proxyIpPool.forEach((k,v)->{
//            System.out.println(k+" ---> "+v.toString());
//        });

//        rolaInitService.initCountryCode();
//        System.out.println(RolaProxyFetchService.COUNTRY_CODE.toJSONString());
    }

}
