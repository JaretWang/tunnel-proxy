package com.dataeye.proxy.selector;

import com.dataeye.proxy.bean.ProxyIp;

import java.util.List;

/**
 * 一般的ip选择器包含以下几个功能：
 * 分发ip，维护ip池，自动更换ip，自动剔除劣质ip，监控ip使用情况，监控请求使用情况
 *
 * @author jaret
 * @date 2022/8/17 23:01
 * @description
 */
public interface AbstractIpSelector {

    /**
     * 获取一个ip
     *
     * @return
     */
    ProxyIp getOne();

    /**
     * 获取多个ip
     *
     * @return
     */
    List<ProxyIp> getIpList();

    /**
     * ip池健康检查
     */
    void healthCheck();

    /**
     * 成功率统计
     */
    void successPercentStatistics();

    /**
     * 添加固定数量的ip到ip池
     */
    void addFixedNumIp(int num);

    /**
     * 从ip池中移除ip
     */
    void removeIp(String ip, int port);

}
