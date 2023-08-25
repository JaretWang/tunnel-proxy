package com.dataeye.proxy.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * @author jaret
 * @date 2022/4/27 14:40
 * @description
 */
@Slf4j
public class NetUtils {

    /**
     * 获取eth0网卡对应的ip地址
     *
     * @return
     */
    public static String getEth0Inet4InnerIp() {
        Enumeration<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return null;
        }

        NetworkInterface netInterface;
        while (networkInterfaces.hasMoreElements()) {
            netInterface = networkInterfaces.nextElement();
            if (null != netInterface && "eth0".equals(netInterface.getName())) {
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        String hostAddress = address.getHostAddress();
                        log.info("local ip={}", hostAddress);
                        return hostAddress;
                    }
                }
            }
        }
        return null;
    }

    public static String getEth0Inet4InnerIp(String netCard) {
        Enumeration<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return null;
        }

        NetworkInterface netInterface;
        while (networkInterfaces.hasMoreElements()) {
            netInterface = networkInterfaces.nextElement();
            if (null != netInterface && netCard.equals(netInterface.getName())) {
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        }
        return null;
    }



    /**
     * 获取公网ip
     *
     * @return
     */
    public static String getOuterIp() {
        return OkHttpTool.doGet("https://ipinfo.io/ip").trim();
    }

}
