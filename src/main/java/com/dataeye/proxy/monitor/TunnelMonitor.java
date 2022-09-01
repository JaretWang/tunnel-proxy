package com.dataeye.proxy.monitor;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.selector.normal.ZhiMaOrdinaryIpSelector;
import com.dataeye.proxy.server.ApnProxyServer;
import com.dataeye.proxy.bean.TunnelMonitorLog;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.service.impl.ZhiMaFetchServiceImpl;
import com.dataeye.proxy.utils.*;
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

    public static final TunnelMonitorLog MONITOR_LOG = new TunnelMonitorLog();
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("TunnelMonitor");
    private static final String TCP_CONNECT_NUM = "netstat -ant | grep 'tcp' | wc -l";

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
    ZhiMaOrdinaryIpSelector zhiMaOrdinaryIpSelector;
    @Autowired
    IpMonitorUtils ipMonitorUtils;
    @Autowired
    ReqMonitorUtils reqMonitorUtils;
    @Autowired
    ApnProxyServer apnProxyServer;
    @Autowired
    ZhiMaFetchServiceImpl zhiMaFetchService;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    TunnelInitService tunnelInitService;

    /**
     * 统计监控数据
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void stactics() {
        if (!proxyServerConfig.isEnable()) {
            return;
        }
        try {
            TunnelInstance tunnel = tunnelInitService.getDefaultTunnel();
            if (tunnel == null) {
                logger.error("tunnel instance is null");
                return;
            }
            MONITOR_LOG.setTcpConn(getTcpConn());
            MONITOR_LOG.setName(tunnel.getDomain());
            //MONITOR_LOG.setConcurrency((int) apnProxyServer.getConcurrentLimitHandler().getConnections());
            MONITOR_LOG.setOkPercent(reqMonitorUtils.getPercent() + "%");
            MONITOR_LOG.setCost(reqMonitorUtils.getCostAvg() + " ms");
            MONITOR_LOG.setReqSize(reqMonitorUtils.getReqSize() + " kb");
            MONITOR_LOG.setRespSize(reqMonitorUtils.getRespSize() + " kb");
            MONITOR_LOG.setReqBandwidth(reqMonitorUtils.getReqBandwidth() + " kb/s");
            MONITOR_LOG.setRespBandwidth(reqMonitorUtils.getRespBandwidth() + " kb/s");
            MONITOR_LOG.setSurplusIp(zhiMaFetchService.getSurplusIp());
            MONITOR_LOG.setIpLimit(tunnel.getMaxFetchIpNumEveryDay());
            MONITOR_LOG.setUsedIp(zhiMaFetchService.getFetchIp());
            MONITOR_LOG.setIpPoolSize(zhiMaOrdinaryIpSelector.getValidIpSize(zhiMaOrdinaryIpSelector.getProxyIpPool().get(tunnel.getAlias())));
            MONITOR_LOG.setUpdateTime(TimeUtils.formatLocalDate(LocalDateTime.now()));
            logger.info("监控记录入库: {}", JSON.toJSONString(MONITOR_LOG));
            int count = tunnelInitMapper.addMonitorLog(MONITOR_LOG);
            if (count > 0) {
                logger.info("监控记录入库成功: {}", JSON.toJSONString(MONITOR_LOG));
            }
        } catch (Exception e) {
            logger.info("监控记录入库异常", e);
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
    }

    /**
     * 获取tcp连接数
     */
    public int getTcpConn() {
        String exec = CommandUtils.exec(TCP_CONNECT_NUM);
        if (StringUtils.isBlank(exec)) {
            return 0;
        } else {
            int tcpConn = Integer.parseInt(exec.trim());
            if (tcpConn > 0) {
                return tcpConn;
            }
        }
        return 0;
    }

}
