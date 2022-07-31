package com.dataeye.proxy.controller;

import com.dataeye.proxy.dao.TunnelInitMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * @author jaret
 * @date 2022/7/28 15:05
 * @description
 */
@RestController
public class MonitorController {

    @Autowired
    TunnelInitMapper tunnelInitMapper;

    /**
     * 查询每个隧道的请求监控记录
     */
    @PostMapping
    public void getMonitorLog(@RequestBody Map<String, String> params) {
        String name = params.getOrDefault("name", "");
        String from = params.getOrDefault("from", "");
        String to = params.getOrDefault("to", "");
        String time = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-HH-mm HH:mm:ss"));
        tunnelInitMapper.getMonitorLog(name, time, Integer.parseInt(from), Integer.parseInt(to));
    }

}
