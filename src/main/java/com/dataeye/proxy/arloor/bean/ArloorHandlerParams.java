package com.dataeye.proxy.arloor.bean;

import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.arloor.vo.HttpConfig;
import com.dataeye.proxy.arloor.vo.SslConfig;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArloorHandlerParams {

    ApnProxyRemoteChooser apnProxyRemoteChooser;
    TunnelInstance tunnelInstance;
    RequestDistributeService requestDistributeService;
    ThreadPoolTaskExecutor ioThreadPool;
    RequestMonitor requestMonitor;
    HttpConfig httpConfig;
    SslConfig sslConfig;

}