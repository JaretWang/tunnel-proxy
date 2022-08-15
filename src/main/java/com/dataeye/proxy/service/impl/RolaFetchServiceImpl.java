package com.dataeye.proxy.service.impl;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.ProxyFetchService;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @author jaret
 * @date 2022/8/15 14:50
 * @description
 */
@Service
public class RolaFetchServiceImpl implements ProxyFetchService {

    @Override
    public ProxyIp getOne(TunnelInstance tunnelInstance) throws Exception {
        return null;
    }

    public List<ProxyIp> getIpList(int needIpSize, TunnelInstance tunnelInstance, boolean init){
        Random random = new Random();
        int num = random.nextInt(100000);
        List<ProxyIp> ipList = new LinkedList<>();
        return null;
    }

}
