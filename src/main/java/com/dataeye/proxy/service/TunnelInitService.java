package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jaret
 * @date 2022/4/24 18:06
 * @description
 */
@Service
public class TunnelInitService {

    @Resource
    TunnelInitMapper tunnelInitMapper;

    public List<TunnelInstance> getTunnelList() {
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        return tunnelInstances.stream()
                // 0关闭，1开启
                .filter(element -> element.getEnable() == 1)
                .collect(Collectors.toList());
    }

}
