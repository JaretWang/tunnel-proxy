package com.dataeye.proxy.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/10/16 18:11
 * @description 单次请求记录
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OnceReq {

    private String srcIp;
    private String method;
    private String uri;
    private Integer reqContentLength;
    private Integer code;
    private Integer respContentLength;
    private Integer errorReason;
    private Integer cost;

}
