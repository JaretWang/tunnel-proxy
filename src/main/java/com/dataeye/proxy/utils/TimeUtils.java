package com.dataeye.proxy.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * @author jaret
 * @date 2022/4/15 14:38
 * @description 时间格式转换
 */
public class TimeUtils {

    public static final String FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static String formatLocalDate(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern(FORMAT).format(time);
    }

    public static LocalDateTime str2LocalDate(String time) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FORMAT);
        return LocalDateTime.parse(time, formatter);
    }

    /**
     * 获取当天已经过去的时间，并转化为秒数
     *
     * @return
     */
    public static int toPassedSecond() {
        LocalTime now = LocalTime.now();
        return now.getHour() * 60 * 60 + now.getMinute() * 60 + now.getSecond();
    }

    /**
     * 获取当天已经过去的时间，并转化为秒数
     *
     * @return
     */
    public static int toPassedMin() {
        return toPassedSecond() / 60;
    }

    public static LocalDateTime millisecond2LocalDateTime(long millisecond){
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(millisecond), TimeZone.getDefault().toZoneId());
    }

    public static LocalDateTime second2LocalDateTime(long second){
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(second), TimeZone.getDefault().toZoneId());
    }

    public static void main(String[] args) {
        LocalDateTime expireTime = TimeUtils.second2LocalDateTime(1661829512L);
        System.out.println(expireTime);
    }

}
