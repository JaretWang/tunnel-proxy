package com.dataeye.proxy;

import java.util.Timer;
import java.util.TimerTask;

/**
 * java演示倒计时
 */
public class TimeTest {
    public static int duration = 5 * 60;

    public static void main(String[] args) {
//         time1();
//        time2();
    }

    /**
     * 方式三： 使用java.util.Timer类进行倒计时
     */
    private static void time1() {
        TimerTask timerTask = new TimerTask() {
            public void run() {
                duration--;
                long hh = duration / 60 / 60 % 60;
                long mm = duration / 60 % 60;
                long ss = duration % 60;
                System.out.println("还剩" + hh + "小时" + mm + "分钟" + ss + "秒");
            }
        };
        Timer timer = new Timer();
        timer.schedule(timerTask, 0, 1000);
    }

    /**
     * 方式一： 给定时长倒计时
     */
    private static void time2() {
        while (duration > 0) {
            duration--;
            try {
                Thread.sleep(1000);
                int hh = duration / 60 / 60 % 60;
                int mm = duration / 60 % 60;
                int ss = duration % 60;
                System.out.println("还剩" + hh + "小时" + mm + "分钟" + ss + "秒");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}