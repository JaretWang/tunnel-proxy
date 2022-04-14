package com.dataeye.proxy.utils;


import com.dataeye.logback.LogbackRollingFileUtil;
import org.slf4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author jaret
 * @date 2022/3/25 19:15
 * @description
 */
public class SHA256Utils {
    private static final Logger log = LogbackRollingFileUtil.getLogger("SHA256Utils");


    /**
     * @param strSrc
     * @return
     */
    public static String hash(String strSrc) {
        MessageDigest md = null;
        String strDes = null;

        byte[] bt = strSrc.getBytes();
        try {
            md = MessageDigest.getInstance("SHA-256");

            md.update(bt);
            strDes = bytes2Hex(md.digest()); // to HexString
        } catch (NoSuchAlgorithmException e) {
            log.error("Invalid algorithm.");
            return null;
        }
        return strDes;
    }

    /**
     * Bytes to Hex
     *
     * @param bts
     * @return
     */
    private static String bytes2Hex(byte[] bts) {
        StringBuffer buf = new StringBuffer();
        String tmp = null;
        for (int i = 0; i < bts.length; i++) {
            tmp = (Integer.toHexString(bts[i] & 0xFF));
            if (tmp.length() == 1) {
                buf.append("0");
            }
            buf.append(tmp);
        }
        return buf.toString();
    }
}
