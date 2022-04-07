package com.dataeye.proxy.apn.cons;

import com.dataeye.proxy.bean.dto.TunnelInstance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author jaret
 * @date 2022/4/7 11:05
 * @description
 */
public class Global {

    public static final String NOW_DATE = LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYY-MM-dd hh:mm:ss"));
    public static final TunnelInstance TUNNEL_INSTANCE = TunnelInstance.builder().id(1).alias("local")
            .ip("0.0.0.0").port(21331).proxyUsername("dataeye").proxyPassword("dataeye++123")
            .concurrency(1000).bossThreadSize(1).workerThreadSize(3)
            .businessThreadSize(10).proxyIpPoolSize(2).singleIpTtl(300)
            .updateIpPoolCronExpress("0 0/5 * * * ?").maxNetBandwidthSize(10)
            .lastModified(NOW_DATE).createTime(NOW_DATE).description("local test")
            .connectTimeoutMillis(10000)
            .build();

}
