package com.dataeye.proxy;

import com.dataeye.proxy.dao.TunnelInitMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author jaret
 * @date 2022/3/18 13:19
 * @description
 */
@EnableScheduling
@EnableConfigurationProperties
@SpringBootApplication
@MapperScan("com.dataeye.proxy.dao")
public class TunnelProxyApplication implements CommandLineRunner {

    private static final String MONITOR_IP = "192.168.88.1";
    private static final String MONITOR_PORT = "21330";
    @Autowired
    TunnelInitMapper tunnelInitMapper;

//    static {
//        String eth0Inet4InnerIp = NetUtils.getEth0Inet4InnerIp();
//        if (MONITOR_IP.equals(eth0Inet4InnerIp)) {
//            System.setProperty("proxy.server.enable", "false");
//            System.setProperty("server.port", MONITOR_PORT);
//            System.out.println("ip=" + eth0Inet4InnerIp + ", 关闭隧道，作为监控服务");
//        }
//    }

    public static void main(String[] args) {
        SpringApplication.run(TunnelProxyApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
//        List<TunnelMonitorLog> data = tunnelInitMapper.getMonitorLog("youliang", "2022-07-30%", 0, 10);
//        System.out.println(JSON.toJSONString(data));
    }
}
