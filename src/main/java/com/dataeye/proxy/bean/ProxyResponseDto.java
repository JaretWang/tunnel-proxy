package com.dataeye.proxy.bean;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jaret
 * @date 2022/4/14 10:43
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProxyResponseDto {

    Boolean success;

    String code;

    String msg;

    List<Data> data = new ArrayList<>();


    @lombok.Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static final class Data {
        private String proxyId;
        /**
         * 实例唯一标识
         */
        private String instanceKey;
        /**
         * 渠道
         */
        private Integer channel;
        /**
         * IP
         */
        private String ip;

        /**
         * 端口
         */
        private Integer port;
        /**
         * 用户名
         */
        private String userName;
        /**
         * 密码
         */
        private String password;
        /**
         * 协议类型
         */
        private Integer protocolType;
        /**
         * 过期时间
         */
        private String expireTime;

        /**
         * 实例id
         */
        private String instanceId;
    }

}