package com.dataeye.proxy.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * @author jaret
 * @date 2022/11/2 17:10
 * @description
 */
public class HostNamePortUtils {

    public static String getHostName(String addr) {
        return StringUtils.split(addr, ": ")[0];
    }

    public static int getPort(String addr, int defaultPort) {
        String[] ss = StringUtils.split(addr, ": ");
        if (ss.length == 2) {
            return Integer.parseInt(ss[1]);
        }
        return defaultPort;
    }

}