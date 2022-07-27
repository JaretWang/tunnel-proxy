package com.dataeye.proxy.component;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.apn.ApnProxyServer;
import com.dataeye.proxy.apn.utils.ReqMonitorUtils;
import com.dataeye.proxy.bean.TunnelMonitorLog;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.NetUtils;
import com.dataeye.proxy.utils.TimeUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * @author jaret
 * @date 2022/7/27 17:02
 * @description
 */
@Data
@Component
public class TunnelMonitor {

    //    public static final AtomicReference<TunnelMonitorLog> MONITOR_LOG = new AtomicReference<TunnelMonitorLog>();
    public static final TunnelMonitorLog MONITOR_LOG = new TunnelMonitorLog();
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("TunnelMonitor");

    static {
        String eth0Inet4InnerIp = NetUtils.getEth0Inet4InnerIp();
        if (StringUtils.isBlank(eth0Inet4InnerIp)) {
            MONITOR_LOG.setLocation("unknown");
        } else {
            MONITOR_LOG.setLocation(eth0Inet4InnerIp);
        }
    }

    @Resource
    TunnelInitMapper tunnelInitMapper;
    @Autowired
    IpSelector ipSelector;
    @Autowired
    IpMonitorUtils ipMonitorUtils;
    @Autowired
    ReqMonitorUtils reqMonitorUtils;
    @Autowired
    ApnProxyServer apnProxyServer;
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchService;
    @Autowired
    TunnelInitService tunnelInitService;

    /**
     * 统计监控数据
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void stactics() {
        try {
            TunnelInstance tunnel = tunnelInitService.getDefaultTunnel();
            MONITOR_LOG.setName(tunnel.getAlias());
            MONITOR_LOG.setConcurrency((int) apnProxyServer.getConcurrentLimitHandler().getConnections());
            MONITOR_LOG.setOkPercent(reqMonitorUtils.getPercent() + "%");
            MONITOR_LOG.setCost(reqMonitorUtils.getCostAvg() + " ms");
            MONITOR_LOG.setReqSize(reqMonitorUtils.getReqSize() + " kb");
            MONITOR_LOG.setRespSize(reqMonitorUtils.getRespSize() + " kb");
            MONITOR_LOG.setReqBandwidth(reqMonitorUtils.getReqBandwidth() + " kb/s");
            MONITOR_LOG.setRespBandwidth(reqMonitorUtils.getRespBandwidth() + " kb/s");
            MONITOR_LOG.setSurplusIp(zhiMaFetchService.getSurplusIp());
            MONITOR_LOG.setIpLimit(tunnel.getMaxFetchIpNumEveryDay());
            MONITOR_LOG.setUsedIp(zhiMaFetchService.getFetchIp());
            MONITOR_LOG.setIpPoolSize(ipSelector.getValidIpSize(ipSelector.getProxyIpPool().get(tunnel.getAlias())));
            MONITOR_LOG.setUpdateTime(TimeUtils.formatLocalDate(LocalDateTime.now()));
            logger.info("监控记录入库: {}", JSON.toJSONString(MONITOR_LOG));
            tunnelInitMapper.monitor(MONITOR_LOG);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            MONITOR_LOG.setName("");
            MONITOR_LOG.setConcurrency(0);
            MONITOR_LOG.setOkPercent("");
            MONITOR_LOG.setCost("");
            MONITOR_LOG.setReqSize("");
            MONITOR_LOG.setRespSize("");
            MONITOR_LOG.setReqBandwidth("");
            MONITOR_LOG.setRespBandwidth("");
            MONITOR_LOG.setTcpConn(0);
            MONITOR_LOG.setSurplusIp(0);
            MONITOR_LOG.setIpLimit(0);
            MONITOR_LOG.setUsedIp(0);
            MONITOR_LOG.setIpLimit(0);
            MONITOR_LOG.setUpdateTime("");
        }
//        Process exec = Runtime.getRuntime().exec("netstat -ant | wc -l");
//        OutputStream outputStream = exec.getOutputStream();
//        if (outputStream == null) {
//            MONITOR_LOG.setTcpConn(0);
//        } else {
//            MONITOR_LOG.setTcpConn();
//        }
    }

    @Scheduled(cron = "0 0 0 ? * SUN")
    public void clean() {
        System.out.println("清除以前的日志记录");
        // TODO 清除以前的日志记录
    }

}
