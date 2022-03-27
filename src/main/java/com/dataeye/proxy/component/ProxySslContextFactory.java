package com.dataeye.proxy.component;

import com.dataeye.proxy.config.ProxyServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * @author jaret
 * @date 2022/3/25 17:51
 * @description
 */
@Slf4j
@Component
public class ProxySslContextFactory {

    @Autowired
    private ProxyServerConfig proxyServerConfig;

    public SSLEngine createClientSslEnginForRemoteAddress(String host, int port) {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = null;
            if (proxyServerConfig.isUseTrustStore()) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                KeyStore tks = KeyStore.getInstance("JKS");
                String trustStorePath = proxyServerConfig.getTrustStorePath();
                String trustStorePassword = proxyServerConfig.getTrustStorePassword();
                FileInputStream inputStream = new FileInputStream(trustStorePath);
                tks.load(inputStream, trustStorePassword.toCharArray());
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
     * 创建 SslEngine
     * @return
     */
    public SSLEngine createServerSslEngine() {

        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");

            KeyStore ks = KeyStore.getInstance("JKS");
            KeyStore tks = KeyStore.getInstance("JKS");

            String keyStorePath = proxyServerConfig.getKeyStorePath();
            String keyStorePassword = proxyServerConfig.getKeyStorePassword();
            String trustStorePath = proxyServerConfig.getTrustStorePath();
            String trustStorePassword = proxyServerConfig.getTrustStorePassword();
            ks.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
            tks.load(new FileInputStream(trustStorePath), trustStorePassword.toCharArray());

            kmf.init(ks, keyStorePassword.toCharArray());
            tmf.init(tks);

            sslcontext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            SSLEngine sslEngine = sslcontext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            // should config?
            sslEngine.setNeedClientAuth(false);

            return sslEngine;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;

    }

}