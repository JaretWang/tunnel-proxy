package com.dataeye.proxy.selector;

import com.dataeye.proxy.bean.ProxyIp;

/**
 * 一般的ip选择器包含以下几个功能：
 * 分发ip，维护ip池，自动更换ip，自动剔除劣质ip，监控ip使用情况，监控请求使用情况
 *
 * @author jaret
 * @date 2022/8/17 23:01
 * @description
 */
public interface IpSelectorAdapter {

    ProxyIp getOne();

}
