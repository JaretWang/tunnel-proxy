package com.dataeye.proxy.utils;


import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * @author jaret
 * @date 2022/11/2 18:23
 * @description
 */
public class SSLContextFactory {

    public static SSLContext getSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1");
        KeyStore ks = KeyStore.getInstance("JKS");
        //加载keytool 生成的文件
        // add ssl
        String keyStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\ssl\\tunnel-server-keystore.jks";
//        String trustStorePath = System.getProperty("user.dir") + "\\src\\main\\resources\\ssl\\tunnel-server-truststore.jks";
        char[] passArray = "123456".toCharArray();
        FileInputStream inputStream = new FileInputStream(keyStorePath);
        ks.load(inputStream, passArray);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, passArray);
        sslContext.init(kmf.getKeyManagers(), null, null);
        inputStream.close();
        return sslContext;

    }
}