package com.dataeye.proxy.cons;

import io.netty.util.AttributeKey;
import org.apache.tomcat.util.http.parser.Authorization;

/**
 * @author jaret
 * @date 2022/3/25 0:16
 * @description
 */
public interface HandlerCons {

    int HTTP_OBJECT_AGGREGATOR_SIZE = 10240;
    int DEFAULT_HTTP_PORT = 80;
    int DEFAULT_HTTPS_PORT = 443;
    String LOCALHOST_IP = "127.0.0.1";
    String LOCALHOST_NAME = "localhost";
    String PROTOCOL_HTTP = "http";
    AttributeKey<String> REQUST_URL_ATTRIBUTE_KEY = AttributeKey.valueOf("apnproxy.request_url");
    String LOG4J_CONFIG_FILE = "conf/log4j.xml";
    String CONFIG_FILE = "conf/config.xml";
    String REMOTE_RULES_CONFIG_FILE = "conf/remote-rules.xml";
    String CACHE_DIR = "cache";
    String CACHE_DATA_DIR = "data";
    String HEADER_PROXY_AUTHORIZATION = "Proxy-Authorization";

    int connectTimeoutMillis = 1000;
    double loadFactor = 0.5;


}
