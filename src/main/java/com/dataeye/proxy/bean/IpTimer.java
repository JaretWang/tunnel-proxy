package com.dataeye.proxy.bean;

import com.dataeye.proxy.component.TimeCountDown;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private TimeCountDown timeCountDown;

}
