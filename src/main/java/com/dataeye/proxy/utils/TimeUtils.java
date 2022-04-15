package com.dataeye.proxy.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author jaret
 * @date 2022/4/15 14:38
 * @description
 */
public class TimeUtils {

    public static final String FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static String formatLocalDate(LocalDateTime time){
        return DateTimeFormatter.ofPattern(FORMAT).format(time);
    }

}
