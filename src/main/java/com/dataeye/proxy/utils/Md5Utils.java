package com.dataeye.proxy.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * @author jaret
 * @date 2022/3/18 16:05
 * @description
 */
public class Md5Utils {

    /**
     * MD5加密 生成32位md5码
     *
     * @param inStr 输入字符串
     * @return 返回32位md5码
     */
    public static String md5Encode(String inStr) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
            return "";
        }

        byte[] byteArray = inStr.getBytes(StandardCharsets.UTF_8);
        byte[] md5Bytes = md5.digest(byteArray);
        StringBuilder hexValue = new StringBuilder();
        for (byte md5Byte : md5Bytes) {
            int val = ((int) md5Byte) & 0xff;
            if (val < 16) {
                hexValue.append("0");
            }
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString();
    }

}
