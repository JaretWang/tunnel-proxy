package com.dataeye.proxy.utils;

import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jaret
 * @date 2022/6/24 14:13
 * @description 堆外内存监控工具
 */
@Component
public class DirectMemoryUtils {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("DirectMemoryUtils");
    private static final ScheduledThreadPoolExecutor SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolConfig.TunnelThreadFactory("direct-memory-monitor-"), new ThreadPoolExecutor.AbortPolicy());
    private static AtomicLong DIRECT_MEM = new AtomicLong(0);
    @Autowired
    ProxyServerConfig proxyServerConfig;

    public void init() throws IllegalAccessException {
        Field field = ReflectionUtils.findField(PlatformDependent.class, "DIRECT_MEMORY_COUNTER");
        if (field == null) {
            throw new RuntimeException("DIRECT_MEMORY_COUNTER is null");
        }
        field.setAccessible(true);
        DIRECT_MEM = (AtomicLong) field.get(PlatformDependent.class);
        SCHEDULE_EXECUTOR.scheduleAtFixedRate(this::doReport, 0, 60, TimeUnit.SECONDS);
    }

    public void doReport() {
        logger.info("netty direct memory size={} byte, max={} byte", DIRECT_MEM.get(), PlatformDependent.maxDirectMemory());
    }

}