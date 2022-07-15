package com.dataeye.proxy.apn.remotechooser;

import com.dataeye.proxy.apn.bean.ProxyIp;
import com.dataeye.proxy.apn.config.ApnProxyListenType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
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
    IpSelector ipSelector;
//    @Autowired
//    IpPoolScheduleService ipPoolScheduleService;
//    public ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) throws InterruptedException {
//        String proxyServer = tunnelInstance.getAlias();
//        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipPoolScheduleService.getProxyIpPool();
//        ConcurrentLinkedQueue<ProxyIp> proxyCfgsQueue = proxyIpPool.get(proxyServer);
//        if (Objects.isNull(proxyCfgsQueue)) {
//            logger.error("没有检测到隧道 {} 的ip池", proxyServer);
//            ipPoolScheduleService.initSingleServer(tunnelInstance);
//            return getProxyConfig(tunnelInstance);
//        }
//        ProxyIp poll = proxyCfgsQueue.poll();
//        if (Objects.isNull(poll)) {
//            try {
//                Thread.sleep(1000L);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            logger.error("从队列中poll出来的ip为空");
//            return getProxyConfig(tunnelInstance);
//        }
//        // 只取有效的
//        boolean valid = poll.getValid().get();
//        if (!valid) {
//            logger.info("ip={} 已失效, 即将被移除", poll.getIpAddr());
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
//        proxyCfgsQueue.offer(poll);
//        return apPlainRemote;
//    }

    public ApnProxyRemote getProxyConfig(TunnelInstance tunnelInstance) throws InterruptedException {
        String proxyServer = tunnelInstance.getAlias();
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ProxyIp>> proxyIpPool = ipSelector.getProxyIpPool();
        ConcurrentLinkedQueue<ProxyIp> proxyCfgsQueue = proxyIpPool.get(proxyServer);
        if (Objects.isNull(proxyCfgsQueue)) {
            logger.error("queue is not exist");
            Thread.sleep(1500L);
            return getProxyConfig(tunnelInstance);
        }
        ProxyIp poll = proxyCfgsQueue.poll();
        if (Objects.isNull(poll)) {
            logger.error("the ip from queue is null");
            Thread.sleep(1500L);
            return getProxyConfig(tunnelInstance);
        }
        // 只取有效的
        boolean valid = poll.getValid().get();
        if (!valid) {
            logger.info("ip={} is invalid and will be removed", poll.getIpAddr());
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
