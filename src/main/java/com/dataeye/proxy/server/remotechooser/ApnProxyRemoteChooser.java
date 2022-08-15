package com.dataeye.proxy.server.remotechooser;

import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.config.ApnProxyListenType;
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
        ApnProxyRemote apPlainRemote = adapte(poll);
        // 取了需要再放进去
        proxyCfgsQueue.offer(poll);
        return apPlainRemote;
    }

    /**
     * 适配芝麻ip，rola静态ip
     * @param poll
     * @return
     */
    public ApnProxyRemote adapte(ProxyIp poll) {
        if (poll == null) {
            return null;
        }
        ApnProxyRemote apPlainRemote = new ApnProxyPlainRemote();
        apPlainRemote.setAppleyRemoteRule(true);
        apPlainRemote.setRemoteListenType(ApnProxyListenType.PLAIN);
        apPlainRemote.setRemoteHost(poll.getHost());
        apPlainRemote.setRemotePort(poll.getPort());
        apPlainRemote.setProxyUserName(poll.getUserName());
        apPlainRemote.setProxyPassword(poll.getPassword());
        apPlainRemote.setExpireTime(poll.getExpireTime());
        return apPlainRemote;
    }

    /**
     * 适配 rola 动态ip
     * @param poll
     * @return
     */
    public ApnProxyRemote adapteRolaDynamicIp(ProxyIp poll) {
        ApnProxyRemote adapte = adapte(poll);
        String subAccount = poll.getUserName() + poll.getRolaAccountNum().get();
        adapte.setProxyUserName(subAccount);
        poll.getRolaAccountNum().incrementAndGet();
        return adapte;
    }

}
