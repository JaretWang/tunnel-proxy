package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.dto.TunnelInstance;

/**
 * @author jaret
 * @date 2022/4/21 11:35
 * @description
 */
public interface ProxyFetchService {

    /**
     * 获取一个ip
     *
     * @return
     * @throws Exception
     */
    ProxyIp getOne(TunnelInstance tunnelInstance) throws Exception;

}
