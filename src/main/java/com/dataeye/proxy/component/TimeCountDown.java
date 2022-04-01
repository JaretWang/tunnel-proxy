package com.dataeye.proxy.component;

import com.dataeye.proxy.config.ProxyServerConfig;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Timer;
import java.util.TimerTask;

/**
 * @author jaret
 * @date 2022/3/29 9:52
 * @description
 */
@Data
@Component
@Scope("prototype")
public class TimeCountDown {

    @Autowired
    private transient ProxyServerConfig proxyServerConfig;
    /**
     * 倒计时时间，单位：秒
     */
    public int duration = 0;

    public TimeCountDown(){ }

    public TimeCountDown(int duration){
        this.duration = duration;
    }

    /**
     * 倒计时器
     */
    public void countdownByTimer() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                duration--;
//                long hh = duration / 60 / 60 % 60;
//                long mm = duration / 60 % 60;
//                long ss = duration % 60;
//                System.out.println("还剩" + hh + "小时" + mm + "分钟" + ss + "秒");
            }
        };
        Timer timer = new Timer();
        timer.schedule(timerTask, 0, 1000);
    }

    public boolean isEffective() {
        return duration > 0;
    }
}
