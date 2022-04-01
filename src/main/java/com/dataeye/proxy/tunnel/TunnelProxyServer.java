package com.dataeye.proxy.tunnel;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.service.ProxyService;
import com.dataeye.proxy.tunnel.initializer.TunnelProxyServerChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jaret
 * @date 2022/3/17 21:52
 * @description 隧道代理服务
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
public class TunnelProxyServer {

    @Autowired
    private ProxyServerConfig proxyServerConfig;
    @Autowired
    private ProxySslContextFactory proxySslContextFactory;
    @Autowired
    private IpSelector ipSelector;
    @Autowired
    private ProxyService proxyService;
    @Resource(name = "ioThreadPool")
    private ThreadPoolTaskExecutor ioThreadPool;
    @Autowired
    private ITunnelDistributeService tunnelDistributeService;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * 根据配置参数启动
     */
    public void startByConfig(List<TunnelInstance> tunnelInstances) {
        int size = tunnelInstances.size();
        ThreadPoolTaskExecutor threadPoolTaskExecutor = getTunnelThreadpool(size);
        tunnelInstances.forEach(instance -> threadPoolTaskExecutor.submit(new CreateProxyServerTask(instance)));
        log.info("根据配置参数共启动 [{}] 个 proxy server", tunnelInstances.size());
    }

    /**
     * 根据隧道实例个数初始化线程池
     *
     * @param instanceSize proxy server 实例个数
     * @return
     */
    public ThreadPoolTaskExecutor getTunnelThreadpool(int instanceSize) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(instanceSize);
        pool.setMaxPoolSize(instanceSize + 1);
        pool.setQueueCapacity(2 * instanceSize);
        pool.setKeepAliveSeconds(60);
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setThreadNamePrefix("tunnel_create");
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.initialize();
        log.info("隧道初始化线程池创建完成");
        return pool;
    }

    /**
     * 创建proxy server
     */
    class CreateProxyServerTask implements Runnable {

        private final TunnelInstance tunnelInstance;

        public CreateProxyServerTask(TunnelInstance tunnelInstance) {
            this.tunnelInstance = tunnelInstance;
        }

        @Override
        public void run() {
            String alias = tunnelInstance.getAlias();
            String host = tunnelInstance.getIp();
            int port = tunnelInstance.getPort();
            int bossThreadSize = tunnelInstance.getBossThreadSize();
            int workerThreadSize = tunnelInstance.getWorkerThreadSize();

            EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreadSize);
            EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreadSize);
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .localAddress(host, port)
                    .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new TunnelProxyServerChannelInitializer(proxyServerConfig, proxySslContextFactory,
                            tunnelDistributeService, tunnelInstance, ipSelector, proxyService))
                    .childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
            try {
                ChannelFuture future = serverBootstrap.bind().sync();
                log.info("代理服务器 [{}] 启动成功, ip: {}, port: {}", alias, host, port);
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("启动代理服务器时，出现异常：{}", e.getMessage());
                e.printStackTrace();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }
    }

    /**
     * 启动一个代理服务器
     */
    public void start() {
        String host = proxyServerConfig.getHost();
        int port = proxyServerConfig.getPort();
        bossGroup = new NioEventLoopGroup(proxyServerConfig.getBossThreadCount());
        workerGroup = new NioEventLoopGroup(proxyServerConfig.getWorkerThreadCount());

        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .localAddress(host, port)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new TunnelProxyServerChannelInitializer(proxyServerConfig, proxySslContextFactory,
                        tunnelDistributeService, null, ipSelector, proxyService))
                .childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
        try {
            ChannelFuture future = serverBootstrap.bind().sync();
            log.info("代理服务器启动成功, ip: {}, port: {}", host, port);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("启动代理服务器时，出现异常：{}", e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        log.warn("关闭代理服务器相关线程池");
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

}
