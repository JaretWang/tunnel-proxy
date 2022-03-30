package com.dataeye.proxy.controller;

import com.dataeye.proxy.service.ITunnelDistributeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jaret
 * @date 2022/3/30 9:46
 * @description
 */
@Slf4j
@RestController("/tunnel")
public class TunnelDistributeController {

    @Autowired
    ITunnelDistributeService iTunnelDistributeService;

    /**
     * 根据mysql参数，初始化隧道
     */
    @GetMapping("/manage/list")
    public void initTunnel(){

    }

}
