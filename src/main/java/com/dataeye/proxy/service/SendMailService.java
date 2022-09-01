package com.dataeye.proxy.service;

import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.starter.httpclient.HttpClientResponse;
import com.dataeye.starter.httpclient.ResponseEntityType;
import com.dataeye.starter.httpclient.simple.SimpleHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;

/**
 * @author jaret
 * @date 2022/9/1 12:34
 * @description
 */
@Service
public class SendMailService {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("SendMailService");
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    private SimpleHttpClient simpleHttpClient;

    public void sendMail(String subject, String content) {
        if (StringUtils.isBlank(subject) || StringUtils.isBlank(content)) {
            logger.error("邮件发送失败, 内容为空, subject: {}, content: {}", subject, content);
            return;
        }
        String to = "";
        try {
            subject = URLEncoder.encode(subject, "UTF-8");
            content = URLEncoder.encode(content, "UTF-8");
            to = URLEncoder.encode(proxyServerConfig.getPrincipal(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String requestUrl = MessageFormat.format(proxyServerConfig.getMailSendAddr(), subject, content, to);
        HttpClientResponse response = simpleHttpClient.doGet(requestUrl, null, ResponseEntityType.STRING_UTF8, false);
        if (response.codeIs200()) {
            logger.info("邮件发送成功, subject: {}, content: {}", subject, content);
            return;
        }
        logger.error("邮件发送失败, subject: {}, content: {}, code: {}, resp: {}", subject, content, response.getStatusCode(), response.getResponseContent());
    }

}