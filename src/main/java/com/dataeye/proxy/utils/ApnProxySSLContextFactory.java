package com.dataeye.proxy.utils;


import com.dataeye.proxy.config.ApnProxyConfig;
import org.slf4j.Logger;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * @author jaret
 * @date 2022/4/14 10:40
 */
public class ApnProxySSLContextFactory {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    public static SSLEngine createSslEngine(String host, int port) {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = null;
            if (ApnProxyConfig.getConfig().isUseTrustStore()) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                KeyStore tks = KeyStore.getInstance("JKS");
                tks.load(new FileInputStream(ApnProxyConfig.getConfig().getTrustStorePath()),
                        ApnProxyConfig.getConfig().getTrustStorePassword().toCharArray());
                tmf.init(tks);
                trustManagers = tmf.getTrustManagers();
            }

            sslcontext.init(null, trustManagers, null);

            return sslcontext.createSSLEngine(host, port);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public static SSLEngine createSslEngine(String keyStorePath, String keyStorePassword, String trustStorePath, String trustStorePassword) {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");

            KeyStore ks = KeyStore.getInstance("JKS");
            KeyStore tks = KeyStore.getInstance("JKS");
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
            logger.error(e.getMessage(), e);
        }
        return null;
    }

}
