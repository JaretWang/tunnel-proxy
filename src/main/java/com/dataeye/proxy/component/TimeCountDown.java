package com.dataeye.proxy.component;

import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.Global;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
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
public class TimeCountDown implements InitializingBean {

    @Autowired
    private transient ProxyServerConfig proxyServerConfig;
    public int duration;

    /**
     * 倒计时
     */
    public void countdownByTimer() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                duration--;
            }
        };
        Timer timer = new Timer();
        timer.schedule(timerTask, 0, 1000);
    }

    @Override
    public void afterPropertiesSet() {
        duration = proxyServerConfig.getTimerDuration();
        countdownByTimer();
    }

}
