package com.dataeye.proxy.apn;

import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.handler.ConcurrentLimitHandler;
import com.dataeye.proxy.apn.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 代理服务器
 *
 * @author jaret
 * @date 2022/4/7 10:30
 */
@Component
public class ApnProxyServer {

    private static final Logger LOG = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private static final java.lang.String LOCAL_ADDRESS = "0.0.0.0";

    @Autowired
    ApnProxyRemoteChooser apnProxyRemoteChooser;
    @Autowired
    RequestDistributeService requestDistributeService;
    @Resource
    TunnelInitService tunnelInitService;

    /**
     * 初始化隧道实例
     */
    @PostConstruct
    public void initMultiTunnel() {
        // 获取初始化参数
        List<TunnelInstance> tunnelList = tunnelInitService.getTunnelList();
        // 创建实例
        startByConfig(tunnelList);
        // 堆外内存： 查看当前Netty程序是否使用noCleaner策略
//        PlatformDependent.useDirectBufferNoCleaner();
        // 该类会采样应用程序中%1的buffer分配，并进行跟踪。检测堆外内存的泄露。目前检测级别有4种：DISABLE, SIMPLE(默认),ADVANCED, PARANOID
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
    }

    /**
     * 根据配置参数启动
     */
    public void startByConfig(List<TunnelInstance> tunnelInstances) {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = getTunnelThreadpool(tunnelInstances.size(), "tunnel_create_");
        tunnelInstances.forEach(instance -> threadPoolTaskExecutor.submit(() -> startProxyServer(instance)));
        System.out.println("根据配置参数共启动 " + tunnelInstances.size() + " 个隧道server");
    }

    /**
     * 根据隧道实例个数初始化线程池
     *
     * @param instanceSize proxy server 实例个数
     * @return
     */
    public ThreadPoolTaskExecutor getTunnelThreadpool(int instanceSize, String threadNamePrefix) {
        return getTunnelThreadpool(instanceSize, instanceSize, 2 * instanceSize, threadNamePrefix);
    }

    public ThreadPoolTaskExecutor getTunnelThreadpool(int coreSize, int maxSize, int queueSize, String threadNamePrefix) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(coreSize);
        pool.setMaxPoolSize(maxSize);
        pool.setQueueCapacity(queueSize);
        pool.setKeepAliveSeconds(60);
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setThreadNamePrefix(threadNamePrefix);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.initialize();
        return pool;
    }

    /**
     * 启动一个 proxy server
     *
     * @param tunnelInstance 隧道实例
     */
    private void startProxyServer(TunnelInstance tunnelInstance) {
        String alias = tunnelInstance.getAlias();
        int port = tunnelInstance.getPort();
        int bossThreadSize = tunnelInstance.getBossThreadSize();
        int workerThreadSize = tunnelInstance.getWorkerThreadSize();

        ApnHandlerParams apnHandlerParams = ApnHandlerParams.builder()
                .apnProxyRemoteChooser(apnProxyRemoteChooser)
                .tunnelInstance(tunnelInstance)
                .requestDistributeService(requestDistributeService)
                .concurrentLimitHandler(new ConcurrentLimitHandler(tunnelInstance))
                .trafficScheduledThreadPool(new ScheduledThreadPoolExecutor(10,
                        new ThreadPoolConfig.TunnelThreadFactory("bandwidth-monitor-"),
                        new ThreadPoolExecutor.AbortPolicy()))
                .build();

        EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreadSize);
        EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreadSize);
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .localAddress(LOCAL_ADDRESS, port)
                .channel(NioServerSocketChannel.class)
//                // 当设置值超过64KB时，需要在绑定到本地端口前设置。该值设置的是由ServerSocketChannel使用accept接受的SocketChannel的接收缓冲区。
//                .option(ChannelOption.SO_RCVBUF, 1024)
//                // 服务端接受连接的队列长度，如果队列已满，客户端连接将被拒绝。默认值，Windows默认为200，linux为128。
//                .option(ChannelOption.SO_BACKLOG, 1024)
//                // 修复 failed to allocate 2048 byte(s) of direct memory
//                .childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                .childHandler(new ApnProxyServerChannelInitializer(apnHandlerParams))
                // 为了避免tcp主动关闭方最后都会等待2MSL才会彻底释放连接,处于TIME-WAIT的连接占用的资源不会被操作系统内核释放,这个时候重启server,就会出现 Address already in use 错误
                .option(ChannelOption.SO_REUSEADDR, true)
                // 连接心跳检测, 默认2小时12分钟后, 关闭不存活的连接
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                // 用于启用或关于Nagle算法。如果要求高实时性，有数据发送时就马上发送，就将该选项设置为true,关闭Nagle算法. 如果要减少发送次数减少网络交互，就设置为false等累积一定大小后再发送。默认为false。
                .childOption(ChannelOption.TCP_NODELAY, true);
        try {
            ChannelFuture future = serverBootstrap.bind().sync();
            System.out.println("隧道服务 [" + alias + "] 启动成功, port=" + port);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("启动代理服务器时，出现异常=" + e.getMessage());
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
