package com.dataeye.proxy.config;


import com.dataeye.logback.LogbackRollingFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jaret
 * @date 2022/3/22 14:46
 * @description
 */

@Configuration
public class ThreadPoolConfig {

    private static final Logger log = LogbackRollingFileUtil.getLogger("ApnProxyServer");

    /**
     * cpu 密集型线程池：核心线程数量=cpu核心数量+1
     *
     * @return
     */
    @Bean("cpuThreadPool")
    public ThreadPoolTaskExecutor cpuThreadPool() {
        int processors = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = processors + 1;
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(processors);
        pool.setMaxPoolSize(maxPoolSize);
        pool.setQueueCapacity(1000);
        pool.setKeepAliveSeconds(1000);
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setThreadNamePrefix("cpu-asyn");
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.initialize();
        log.info("cpu 密集型线程池创建完成");
        return pool;
    }

}
