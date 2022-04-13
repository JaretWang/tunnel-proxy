package com.dataeye.proxy.apn.cons;

import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.util.AttributeKey;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jaret
 * @date 2022/4/7 11:05
 * @description
 */
public class Global {

    public static final String NOW_DATE = LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYY-MM-dd hh:mm:ss"));
    public static final TunnelInstance TUNNEL_INSTANCE = TunnelInstance.builder().id(1).alias("local")
            .ip("0.0.0.0").port(21331).username("dataeye").password("dataeye++123")
            .concurrency(1000).bossThreadSize(1).workerThreadSize(3)
            .businessThreadSize(10).fixedIpPoolSize(2).maxNetBandwidth(10)
            .lastModified(NOW_DATE).createTime(NOW_DATE).description("local test")
            .connectTimeoutMillis(10000)
            .build();

    public static final ConcurrentHashMap<String, ApnProxyRemote> REQUEST_IP_USE_RELATIONS = new ConcurrentHashMap<>();

    public static final AttributeKey<String> REQUST_IP_ATTRIBUTE_KEY = AttributeKey.valueOf("apnproxy.request_ip");

}
