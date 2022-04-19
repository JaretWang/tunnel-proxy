package com.dataeye.proxy.arloor.config;

import com.dataeye.proxy.arloor.vo.HttpConfig;
import com.dataeye.proxy.arloor.vo.SslConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author jaret
 * @date 2022/4/17 14:39
 * @description
 */
public class ArloorConfig {

    public static final boolean USE_PROXY_IP = true;
    public static final HashMap<String, String> SSL_AUTH = new HashMap<String, String>() {
        {
            put("Basic YXJsb29yOmh0dHBmb3Jhcmxvb3I=", "arloor:httpforarloor");
            put("Basic YXJsb29yOmh0dHBmb3Jhcmxvb3LCow==", "arloor:httpforarloor");
        }
    };
    public static final SslConfig SSL_CONFIG = SslConfig.builder()
            .port(21332)
            .auth(SSL_AUTH)
            .fullchain("cert.pem")
            .privkey("privkey.pem")
            .build();
    public static final HttpConfig HTTP_CONFIG = HttpConfig.builder()
            .port(21331)
            .auth(new HashMap<>())
            .domainWhiteList(new HashSet<>(Collections.singletonList("github.com")))
            .build();

}
