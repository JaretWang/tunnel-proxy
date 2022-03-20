package com.dataeye.proxy.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * @author jaret
 * @date 2022/3/18 15:26
 * @description
 */
public class HostNamePortUtils {

    /**
     * 获取主机名
     *
     * @param addr 源地址
     * @return
     */
    public static String getHostName(String addr) {
        return StringUtils.split(addr, ": ")[0];
    }

    /**
     * 获取端口
     *
     * @param addr 源地址
     * @param defaultPort 默认端口
     * @return
     */
    public static int getPort(String addr, int defaultPort) {
        String[] ss = StringUtils.split(addr, ": ");
        if (ss.length == 2) {
            return Integer.parseInt(ss[1]);
        }
        return defaultPort;
    }


}
