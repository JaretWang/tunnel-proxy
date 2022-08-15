package com.dataeye.proxy.service;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.config.ApnProxyListenType;
import com.dataeye.proxy.server.remotechooser.ApnProxyPlainRemote;
import com.dataeye.proxy.server.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.bean.dto.TunnelInstance;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/4/21 11:35
 * @description
 */
public interface ProxyFetchService {

    /**
     * 每日拉取ip数最大值
     */
    AtomicInteger MAX_FETCH_IP_NUM_EVERY_DAY = new AtomicInteger(5000);
    /**
     * 最大重试获取ip次数
     */
    int MAX_RETRY_TIMES = 3;

    /**
     * 获取一个ip
     *
     * @return
     * @throws Exception
     */
    ProxyIp getOne(TunnelInstance tunnelInstance) throws Exception;

    /**
     * ApnProxyRemote 适配器
     *
     * @return
     */
    default ApnProxyRemote apnProxyRemoteAdapter() {
        ProxyIp one = null;
        try {
            one = getOne(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Objects.isNull(one)) {
            throw new RuntimeException("getOne is null");
        }
        ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
        apPlainRemote.setAppleyRemoteRule(true);
        apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
        apPlainRemote.setRemoteHost(one.getHost());
        apPlainRemote.setRemotePort(one.getPort());
        apPlainRemote.setProxyUserName(one.getUserName());
        apPlainRemote.setProxyPassword(one.getPassword());
        apPlainRemote.setExpireTime(one.getExpireTime());
        return apPlainRemote;
    }

}
