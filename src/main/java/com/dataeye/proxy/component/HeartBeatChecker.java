//package com.dataeye.proxy.component;
//
//import com.dataeye.proxy.apn.handler.ConcurrentLimitHandler;
//import com.dataeye.proxy.config.ThreadPoolConfig;
//import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
//import org.slf4j.Logger;
//import org.springframework.stereotype.Component;
//
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.ScheduledThreadPoolExecutor;
//import java.util.concurrent.ThreadPoolExecutor;
//
///**
// * 如果周期性检测到长时间没有连接，或者手动关闭隧道，则关闭拉取ip，关闭请求监控，ip监控。
// *
// * @author jaret
// * @date 2022/7/22 9:31
// * @description 心跳检测
// */
//@Component
//public class HeartBeatChecker {
//
//    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("HeartBeatChecker");
//    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
//            new ThreadPoolConfig.TunnelThreadFactory("Heart-Beat-Checker-"), new ThreadPoolExecutor.AbortPolicy());
//
//    void check(){
//        if (ConcurrentLimitHandler.USED.get()) {
//            logger.info("隧道正在被使用");
//            return;
//        }
//    }
//
//}
