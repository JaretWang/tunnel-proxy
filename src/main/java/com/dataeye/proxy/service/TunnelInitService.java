package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
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

    public static final List<TunnelInstance> TUNNEL_INSTANCES = new LinkedList<>();

    /**
     * 获取所有隧道实例配置列表
     * @return 隧道实例配置列表
     */
    public List<TunnelInstance> getTunnelList() {
        if (!TUNNEL_INSTANCES.isEmpty()) {
            return TUNNEL_INSTANCES;
        }
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        List<TunnelInstance> collect = tunnelInstances.stream()
                // 0关闭，1开启
                .filter(element -> element.getEnable() == 1)
                .collect(Collectors.toList());
        TUNNEL_INSTANCES.addAll(collect);
        return TUNNEL_INSTANCES;
    }

    /**
     * 根据名称获取单个tunnel
     */
    public TunnelInstance getTunnel(String tunnelName) {
        for (TunnelInstance element : getTunnelList()) {
            if (tunnelName.equalsIgnoreCase(element.getAlias())) {
                return element;
            }
        }
        return null;
    }

}
