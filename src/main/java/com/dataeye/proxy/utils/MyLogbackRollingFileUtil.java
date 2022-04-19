package com.dataeye.proxy.utils;

import com.dataeye.logback.LogbackRollingFileUtil;
import org.slf4j.Logger;

public class MyLogbackRollingFileUtil {

    private static String ENCODER_PATTERN = "[%d{yyyy-MM-dd HH:mm:ss}] [%thread] [%file:%M:%line] [%-5level] -> %msg%n";
    private static String ENCODER_PATTERN2 = "[%d{yyyy-MM-dd HH:mm:ss}] [%-5level] [%thread] [%-4relative] [%p] [%c{0}:%L] - %msg %n";

    /**
     * <pre>
     * 获取Logger
     * 其他配置优先读取配置文件，否则用默认值
     * </pre>
     *
     * @param loggerName
     * @return
     */
    public static Logger getLogger(String loggerName) {
        return LogbackRollingFileUtil.getLogger(null, null, loggerName, null, null, ENCODER_PATTERN, null);
    }
}