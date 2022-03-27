package com.dataeye.proxy.utils;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jaret
 * @date 2022/3/18 15:26
 * @description
 */
public class HostNamePortUtils {

    private static final Logger log = LoggerFactory.getLogger(HostNamePortUtils.class);

    /**
     * 获取主机名
     *
     * @param addr 源地址
     * @return 主机名
     */
    public static String getHostName(String addr) {
        return StringUtils.split(addr, ": ")[0];
    }

    /**
     * 获取端口
     *
     * @param addr        源地址
     * @param defaultPort 默认端口
     * @return 端口
     */
    public static int getPort(String addr, int defaultPort) {
        String[] ss = StringUtils.split(addr, ": ");
        if (ss.length == 2) {
            return Integer.parseInt(ss[1]);
        }
        return defaultPort;
    }

    /**
     * 构建目标uri
     *
     * @return 目标url
     */
    public static String buildTargetUri(String remoteHost, int remotePort, boolean https) {
        log.debug("远程地址: {}, 远程端口: {}", remoteHost, remotePort);
        StringBuilder builder = new StringBuilder();
        if (https) {
            builder.append("https://");
        } else {
            builder.append("http://");
        }
        return builder.append(remoteHost).append(":").append(remotePort).toString();
    }

    /**
     * 根据请求头，获取远程地址
     *
     * @param originalHostHeader 原始的地址
     * @param https              是否是https协议
     * @return 新的地址
     */
    public static String getRemoteUrl(String originalHostHeader, boolean https) {
        String originalHost = getHostName(originalHostHeader);
        int originalPort;
        if (!https) {
            originalPort = getPort(originalHostHeader, 80);
        } else {
            originalPort = getPort(originalHostHeader, 443);
        }
        return buildTargetUri(originalHost, originalPort, https);
    }

}
