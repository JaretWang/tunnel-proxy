package com.dataeye.proxy.apn.bean;

import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jaret
 * @date 2022/4/7 13:32
 * @description
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApnHandlerParams {

    ApnProxyRemoteChooser apnProxyRemoteChooser;
    TunnelInstance tunnelInstance;
    RequestDistributeService requestDistributeService;

}
