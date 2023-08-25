package com.dataeye.proxy.service;

import com.dataeye.proxy.config.ProxyServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
@Slf4j
@Service
public class SendMailService {

    @Autowired
    ProxyServerConfig proxyServerConfig;

    public void sendMail(String subject, String content) {
        if (StringUtils.isBlank(subject) || StringUtils.isBlank(content)) {
            log.error("邮件发送失败, 内容为空, subject: {}, content: {}", subject, content);
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
//        HttpClientResponse response = simpleHttpClient.doGet(requestUrl, null, ResponseEntityType.STRING_UTF8, false);
//        if (response.codeIs200()) {
//            log.info("邮件发送成功, subject: {}, content: {}", subject, content);
//            return;
//        }
//        log.error("邮件发送失败, subject: {}, content: {}, code: {}, resp: {}", subject, content, response.getStatusCode(), response.getResponseContent());
    }

}