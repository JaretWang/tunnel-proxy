package com.dataeye.proxy.utils;

import com.dataeye.proxy.config.ProxyServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.context.ApplicationContext;

import javax.annotation.Resource;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * @author jaret
 * @date 2022/3/18 16:55
 * @description
 */
@Slf4j
public class ProxySslContextUtils {

    @Resource
    private static ApplicationContext applicationContext;

    /**
     * 根据 host，port 创建 SSLEngine
     *
     * @param host
     * @param port
     * @return
     */
    public static SSLEngine createClientSslEnginForRemoteAddress(String host, int port) {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = null;
            ProxyServerConfig proxyServerConfig = applicationContext.getBean(ProxyServerConfig.class);
            if (proxyServerConfig.isUseTrustStore()) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                KeyStore tks = KeyStore.getInstance("JKS");
                tks.load(new FileInputStream(proxyServerConfig.getTrustStorePath()),
                        proxyServerConfig.getTrustStorePassword().toCharArray());
                tmf.init(tks);
                trustManagers = tmf.getTrustManagers();
            }

            sslcontext.init(null, trustManagers, null);

            return sslcontext.createSSLEngine(host, port);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 创建 SSLEngine
     *
     * @param proxyServerConfig
     * @return
     */
    public static SSLEngine createServerSslEngine(ProxyServerConfig proxyServerConfig) {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");

            KeyStore ks = KeyStore.getInstance("JKS");
            KeyStore tks = KeyStore.getInstance("JKS");

            String keyStorePath = proxyServerConfig.getKeyStorePath();
            String keyStorePassword = proxyServerConfig.getKeyStroePassword();

            String trustStorePath = proxyServerConfig.getTrustStorePath();
            String trustStorePassword = proxyServerConfig.getKeyStroePassword();

            ks.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
            tks.load(new FileInputStream(trustStorePath), trustStorePassword.toCharArray());

            String keyPassword = proxyServerConfig.getKeyStroePassword();
            kmf.init(ks, keyPassword.toCharArray());
            tmf.init(tks);

            sslcontext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            SSLEngine sslEngine = sslcontext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            sslEngine.setNeedClientAuth(false);

            return sslEngine;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

}
