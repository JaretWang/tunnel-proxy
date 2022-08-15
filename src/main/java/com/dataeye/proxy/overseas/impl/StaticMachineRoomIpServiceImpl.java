package com.dataeye.proxy.overseas.impl;

import com.dataeye.proxy.bean.enums.RolaProxyType;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.RolaConfig;
import com.dataeye.proxy.overseas.RolaProxyFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author jaret
 * @date 2022/8/10 11:09
 * @description 动态机房ip
 */
@Service
public class StaticMachineRoomIpServiceImpl extends RolaProxyFetchService {

    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    RolaConfig rolaConfig;

    @Override
    public void initIpPool() {
        buildStaticIpPool(rolaConfig, RolaProxyType.STATIC_MACHINE_ROOM);
    }

}
