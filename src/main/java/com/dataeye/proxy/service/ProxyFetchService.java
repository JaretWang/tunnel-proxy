package com.dataeye.proxy.service;

import com.dataeye.commonx.domain.ProxyCfg;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.apn.remotechooser.ApnProxyPlainRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;

import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/21 11:35
 * @description
 */
public interface ProxyFetchService {

    ProxyCfg getOne() throws Exception;

    default ApnProxyRemote apnProxyRemoteAdapter() {
        ProxyCfg one = null;
        try {
            one = getOne();
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
