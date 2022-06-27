package com.dataeye.proxy.service;

import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.utils.TimeUtils;
import com.dataeye.starter.httpclient.HttpClientResponse;
import com.dataeye.starter.httpclient.ResponseEntityType;
import com.dataeye.starter.httpclient.simple.SimpleHttpClient;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.time.LocalDateTime;

@Service
public class SendMailService {

    private static final Logger logger = LogbackRollingFileUtil.getLogger("SendMailService");
    private static final String MAIL_URL = "http://172.18.248.85:8111/mail/send?subject={0}&content={1}&to={2}&isMime=false";
    private static final String ACCOUNT = "wangchaojia@dataeye.com";
    @Autowired
    private SimpleHttpClient simpleHttpClient;

    public void sendMail(String subject, String content) {
        String time = TimeUtils.formatLocalDate(LocalDateTime.now());
        String sendContent = MessageFormat.format("时间：{0} ,  出现：{1}", time, content);
        logger.info("send email --> 主题={}, 内容={}", subject, sendContent);
        try {
            subject = URLEncoder.encode(subject, "UTF-8");
            String to = URLEncoder.encode(ACCOUNT, "UTF-8");
            String requestUrl = MessageFormat.format(MAIL_URL, subject, sendContent, to);
            HttpClientResponse response = simpleHttpClient.doGet(requestUrl, null, ResponseEntityType.STRING_UTF8, false);
            if (response.codeIs200()) {
                logger.info("send complete --> {}", response.getResponseContent());
                return;
            }
            logger.info("send failure --> code:{}", response.getStatusCode());
        } catch (UnsupportedEncodingException e) {
            logger.error("send email error ---> {}", subject, e);
        }
    }
}