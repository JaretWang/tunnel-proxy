package com.dataeye.proxy.utils;

import com.dataeye.proxy.config.ThreadPoolConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jaret
 * @date 2022/7/22 11:44
 * @description
 */
@Slf4j
public class ThreadUtils {

    /**
     * getActiveCount()	线程池中正在执行任务的线程数量
     * getCompletedTaskCount()	线程池已完成的任务数量，该值小于等于taskCount
     * getCorePoolSize()	线程池的核心线程数量
     * getLargestPoolSize()	线程池曾经创建过的最大线程数量。通过这个数据可以知道线程池是否满过，也就是达到了maximumPoolSize
     * getMaximumPoolSize()	线程池的最大线程数量
     * getPoolSize()	线程池当前的线程数量
     * getTaskCount()	线程池已经执行的和未执行的任务总数
     * <p>
     * 监控线程池线程运行情况
     */
    public static void monitorThread(ThreadPoolExecutor threadPoolExecutor) {
        ThreadFactory tf = threadPoolExecutor.getThreadFactory();
        String namePrefix = "未知";
        if (tf instanceof ThreadPoolConfig.TunnelThreadFactory) {
            ThreadPoolConfig.TunnelThreadFactory threadFactory = (ThreadPoolConfig.TunnelThreadFactory) threadPoolExecutor.getThreadFactory();
            namePrefix = threadFactory.getNamePrefix();
        }
        int corePoolSize = threadPoolExecutor.getCorePoolSize();
        int maximumPoolSize = threadPoolExecutor.getMaximumPoolSize();
        int poolSize = threadPoolExecutor.getPoolSize();
        int activeCount = threadPoolExecutor.getActiveCount();
        int queueSize = threadPoolExecutor.getQueue().size();
        long taskCount = threadPoolExecutor.getTaskCount();
        long completedTaskCount = threadPoolExecutor.getCompletedTaskCount();
        log.info("线程组名={}, 核心线程数={}, 最大线程数={}, 池中实际线程数={}, 已提交任务数={}, 执行中线程数={}, 队列剩余任务数={}, 已完成任务数={}",
                namePrefix, corePoolSize, maximumPoolSize, poolSize, taskCount, activeCount, queueSize, completedTaskCount);
    }

}
