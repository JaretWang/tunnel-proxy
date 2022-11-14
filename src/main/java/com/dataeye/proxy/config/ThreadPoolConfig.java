package com.dataeye.proxy.config;

import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jaret
 * @date 2022/4/24 10:36
 * @description
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * ip存活检查线程池
     */
    @Bean("ipAliveCheck")
    public ThreadPoolTaskExecutor ipAliveCheckThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //此方法返回可用处理器的虚拟机的最大数量; 不小于1
        //int core = Runtime.getRuntime().availableProcessors();
        int core = 10;
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(core * 2 + 1);
        executor.setKeepAliveSeconds(60);
        //如果传入值大于0，底层队列使用的是LinkedBlockingQueue,否则默认使用SynchronousQueue
        executor.setQueueCapacity(3 * core);
        executor.setThreadNamePrefix("ip-alive-check-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean("markIpStatus")
    public ThreadPoolTaskExecutor markIpStatusThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int core = 20;
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(core * 2 + 1);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(3 * core);
        executor.setThreadNamePrefix("mark-ip-status-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean("replayVps")
    public ThreadPoolTaskExecutor replayVpsThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int core = 5;
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(core);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(3 * core);
        executor.setThreadNamePrefix("replay-vps-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean("getIpFromAllVps")
    public ThreadPoolTaskExecutor getIpFromAllVpsThreadPool(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int core = 10;
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(5 * core);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(5 * core);
        executor.setThreadNamePrefix("get-ip-from-all-vps-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    /**
     * 自定义线程工厂
     */
    @Data
    public static class TunnelThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public TunnelThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

}
