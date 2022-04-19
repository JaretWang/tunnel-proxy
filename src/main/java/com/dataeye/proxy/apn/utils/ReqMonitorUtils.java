package com.dataeye.proxy.apn.utils;

import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.slf4j.Logger;

/**
 * @author jaret
 * @date 2022/4/18 10:07
 * @description
 */
public class ReqMonitorUtils {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ReqMonitorUtils");

    public static void cost(RequestMonitor requestMonitor, String handler) {
//        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        requestMonitor.setCost(System.currentTimeMillis() - requestMonitor.getBegin());
        logger.info("{} ms, {}, {}, {}, {}, {}, {}, {}",
                requestMonitor.getCost(),
                handler,
                requestMonitor.isSuccess(),
                requestMonitor.getTunnelName(),
                requestMonitor.getProxyAddr(),
                requestMonitor.getFailReason(),
                requestMonitor.getRequestType(),
                requestMonitor.getTargetAddr());
    }

    public static void cost(RequestMonitor requestMonitor) {
        cost(requestMonitor, "未知");
    }

}
