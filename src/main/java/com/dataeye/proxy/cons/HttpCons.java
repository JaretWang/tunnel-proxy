package com.dataeye.proxy.cons;

/**
 * @author jaret
 * @date 2022/11/2 11:27
 * @description
 */
public interface HttpCons {

    String CONNECT = "connect";
    String GET = "get";
    String POST = "post";
    // todo 海外用这个，国内需要换，所以后面要做动态配置
    String VPS_IP_ALIVE_CHECK_URL = "https://www.google.com";
    String DOMESTIC_IP_ALIVE_CHECK_URL = "https://www.baidu.com";
    String OVERSEA__IP_ALIVE_CHECK_URL = "https://www.google.com";

}
