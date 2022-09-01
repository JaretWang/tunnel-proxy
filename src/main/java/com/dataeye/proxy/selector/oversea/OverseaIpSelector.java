package com.dataeye.proxy.selector.oversea;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.selector.CommonIpSelector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author jaret
 * @date 2022/9/1 10:19
 * @description
 */
@Component
public class OverseaIpSelector implements CommonIpSelector {

    @Override
    public ProxyIp getOne() {
        return null;
    }

    @Override
    public List<ProxyIp> getIpList(int count) throws InterruptedException {
        return null;
    }

    @Override
    public void addWhiteList() {

    }

    @Override
    public void healthCheck() {

    }

    @Override
    public void successPercentStatistics() {

    }

    @Override
    public void addFixedNumIp(int num) {

    }

    @Override
    public void removeIp(String ip, int port) {

    }

    @Override
    public ConcurrentLinkedQueue<ProxyIp> getIpPool() {
        return null;
    }

    @Override
    public void init(){

    }

}
