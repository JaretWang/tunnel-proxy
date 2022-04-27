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
        if (time == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern(FORMAT).format(time);
    }

    public static LocalDateTime str2LocalDate(String time){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FORMAT);
        return LocalDateTime.parse(time, formatter);
    }

}
