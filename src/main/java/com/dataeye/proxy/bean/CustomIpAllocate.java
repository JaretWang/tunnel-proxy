package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/8/17 21:47
 * @description
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CustomIpAllocate {

    private int id;
    private String alias;
    private String ip;
    private int port;
    private String outIp;
    private String netCardSeq;

}
