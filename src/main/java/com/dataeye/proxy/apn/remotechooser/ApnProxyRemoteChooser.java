package com.dataeye.proxy.apn.remotechooser;

import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.service.IpPoolScheduleService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author jaret
 * @date 2022/4/14 10:38
 */
@Component
public class ApnProxyRemoteChooser {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    @Autowired
    IpPoolScheduleService ipPoolScheduleService;

    /**
     * 从代理ip池获取ip
     *
     * @param tunnelInstance
     * @return
     */
//    public ProxyIp getProxyIp(TunnelInstance tunnelInstance) throws InterruptedException {
//        if (tunnelInstance == null) {
//            return null;
//        }
//        String alias = tunnelInstance.getAlias();
//        PriorityBlockingQueue<ProxyIp> proxyIps = ipPoolScheduleService.getProxyIpPool().get(alias);
//        if (Objects.isNull(proxyIps)) {
//            logger.error("没有检测到隧道 {} 的ip池", alias);
//            ipPoolScheduleService.initSingleServer(tunnelInstance);
//            return getProxyIp(tunnelInstance);
//        }
//        ProxyIp proxyIp = proxyIps.poll();
//        if (Objects.isNull(proxyIp)) {
//            logger.error("从队列中取出来的ip为空");
//            Thread.sleep(3000L);
//            return getProxyIp(tunnelInstance);
//        }
//        // 只取有效的
//        boolean valid = proxyIp.getValid().get();
//        if (!valid) {
//            logger.info("ip={} 已失效, 移除", proxyIp.getIpAddr());
//            return getProxyIp(tunnelInstance);
//        }
//        // 取了需要再放进去
//        proxyIps.offer(proxyIp);
//        return proxyIp;
//    }

//    public ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) throws InterruptedException {
//        if (tunnelInstance == null) {
//            return null;
//        }
//        String alias = tunnelInstance.getAlias();
//        PriorityBlockingQueue<ProxyIp> proxyIps = ipPoolScheduleService.getProxyIpPool().get(alias);
//        if (Objects.isNull(proxyIps)) {
//            logger.error("没有检测到隧道 {} 的ip池", alias);
//            ipPoolScheduleService.initSingleServer(tunnelInstance);
//            return getProxyConfig(tunnelInstance);
//        }
//        ProxyIp poll = proxyIps.poll();
//        if (Objects.isNull(poll)) {
//            logger.error("从队列中取出来的ip为空");
//            Thread.sleep(3000L);
//            return getProxyConfig(tunnelInstance);
//        }
//        // 只取有效的
//        boolean valid = poll.getValid().get();
//        if (!valid) {
//            logger.info("ip={} 已失效, 移除", poll.getIpAddr());
//            return getProxyConfig(tunnelInstance);
//        }
//
//        ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
//        apPlainRemote.setAppleyRemoteRule(true);
//        apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
//        apPlainRemote.setRemoteHost(poll.getHost());
//        apPlainRemote.setRemotePort(poll.getPort());
//        apPlainRemote.setProxyUserName(poll.getUserName());
//        apPlainRemote.setProxyPassword(poll.getPassword());
//        apPlainRemote.setExpireTime(poll.getExpireTime());
//        // 取了需要再放进去
//        proxyIps.offer(poll);
//        return apPlainRemote;
//    }

    public ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) throws InterruptedException {
        String proxyServer = tunnelInstance.getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
        ConcurrentLinkedQueue<ProxyIp> proxyCfgsQueue = proxyIpPool.get(proxyServer);
        if (Objects.isNull(proxyCfgsQueue)) {
            logger.error("没有检测到隧道 {} 的ip池", proxyServer);
            ipPoolScheduleService.initSingleServer(tunnelInstance);
            return getProxyConfig(tunnelInstance);
        }
        ProxyIp poll = proxyCfgsQueue.poll();
        if (Objects.isNull(poll)) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.error("从队列中poll出来的ip为空");
            return getProxyConfig(tunnelInstance);
        }
        // 只取有效的
        boolean valid = poll.getValid().get();
        if (!valid) {
            logger.info("ip={} 已失效, 即将被移除", poll.getIpAddr());
            return getProxyConfig(tunnelInstance);
        }

        ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
        apPlainRemote.setAppleyRemoteRule(true);
        apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
        apPlainRemote.setRemoteHost(poll.getHost());
        apPlainRemote.setRemotePort(poll.getPort());
        apPlainRemote.setProxyUserName(poll.getUserName());
        apPlainRemote.setProxyPassword(poll.getPassword());
        apPlainRemote.setExpireTime(poll.getExpireTime());
        // 取了需要再放进去
        proxyCfgsQueue.offer(poll);
        return apPlainRemote;
    }

}
