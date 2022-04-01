package com.dataeye.proxy.bean;

import com.dataeye.proxy.component.TimeCountDown;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/3/29 11:42
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IpTimer {

    private String ip;
    private int port;
    private String username;
    private String password;
    private AtomicInteger referenceCount = new AtomicInteger(0);
    private TimeCountDown timeCountDown;

}
