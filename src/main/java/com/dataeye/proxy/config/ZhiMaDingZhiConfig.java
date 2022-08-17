package com.dataeye.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author jaret
 * @date 2022/8/3 16:28
 * @description
 */
@Data
@Component
@ConfigurationProperties(prefix = "proxy.zhima.dingzhi")
public class ZhiMaDingZhiConfig {

    /**
     * 添加ip白名单，在ip=的后面添加自己的ip
     */
    private String addWhilteListUrl;
    /**
     * 一次性提取所有的代理ip （共39个）
     */
    private String getAllIpUrl;
    /**
     * 获取指定数量的ip（注意：通过修改最后一个数字 1-39，指定不同的网卡序号获取ip，多次访问同一个网卡获取的ip是一样的，想要多少个ip，就轮询多少个网卡）
     */
    private String getFixedNumIpUrl;
    /**
     * 指定网卡重拨，更换ip（注意：通过修改最后一个数字 1-39，指定不同的网卡序号进行重新拨号，更换ip）
     */
    private String changeIpUrl;
    /**
     * 进程列表（如：ip:port）
     * key: 进程ip(内网)+port
     * value：进程ip(外网,用于加入白名单)
     */
    private Map<String,String> processList;

}
