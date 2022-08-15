package com.dataeye.proxy.server;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.bean.enums.TunnelType;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.ThreadPoolConfig;
import com.dataeye.proxy.overseas.RolaInitService;
import com.dataeye.proxy.server.handler.ConcurrentLimitHandler;
import com.dataeye.proxy.server.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.server.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.server.service.RequestDistributeService;
import com.dataeye.proxy.service.TunnelInitService;
import com.dataeye.proxy.utils.DirectMemoryUtils;
import com.dataeye.proxy.utils.IpMonitorUtils;
import com.dataeye.proxy.utils.ReqMonitorUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import lombok.Getter;
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

    private static final java.lang.String LOCAL_ADDRESS = "0.0.0.0";

    @Autowired
    IpSelector ipSelector;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    ApnProxyRemoteChooser apnProxyRemoteChooser;
    @Autowired
    RequestDistributeService requestDistributeService;
    @Resource
    TunnelInitService tunnelInitService;
    @Autowired
    RolaInitService rolaInitService;
    @Autowired
    ReqMonitorUtils reqMonitorUtils;
    @Autowired
    IpMonitorUtils ipMonitorUtils;
    @Autowired
    DirectMemoryUtils directMemoryUtils;
    @Getter
    ConcurrentLimitHandler concurrentLimitHandler;

    /**
     * 初始化隧道实例
     */
    @PostConstruct
    public void initTunnel() throws Exception {
        if (!proxyServerConfig.isEnable()) {
            return;
        }
        // 获取eth0网卡内网ip
        tunnelInitService.getEth0Inet4InnerIp();
        // 获取初始化参数
        List<TunnelInstance> tunnelList = tunnelInitService.getTunnelList();
        if (tunnelInitService.getDefaultTunnel().getType() == TunnelType.oversea.seq) {
            // 初始化海外隧道ip池
            rolaInitService.init();
        } else {
            // 初始化国内隧道ip池
            ipSelector.init();
        }
        // ip监控
        ipMonitorUtils.schedule();
        // 请求监控
        reqMonitorUtils.schedule();
        // 创建实例
        startByConfig(tunnelList);
        // 该类会采样应用程序中%1的buffer分配，并进行跟踪。检测堆外内存的泄露。目前检测级别有4种：DISABLE, SIMPLE(默认),ADVANCED, PARANOID
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
        // 启动堆外内存监控工具
        directMemoryUtils.init();
        // 堆外内存： 查看当前Netty程序是否使用noCleaner策略
        // PlatformDependent.useDirectBufferNoCleaner();
    }

    /**
     * 根据配置参数启动
     */
    public void startByConfig(List<TunnelInstance> tunnelInstances) {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = getTunnelThreadpool(tunnelInstances.size(), "tunnel_create_");
        tunnelInstances.forEach(instance -> threadPoolTaskExecutor.submit(() -> startProxyServer(instance)));
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
        int port = tunnelInstance.getPort();
        int bossThreadSize = tunnelInstance.getBossThreadSize();
        int workerThreadSize = tunnelInstance.getWorkerThreadSize();

        concurrentLimitHandler = new ConcurrentLimitHandler(tunnelInstance);
        ApnHandlerParams apnHandlerParams = ApnHandlerParams.builder()
                .ipSelector(ipSelector)
                .tunnelInitService(tunnelInitService)
                .apnProxyRemoteChooser(apnProxyRemoteChooser)
                .tunnelInstance(tunnelInstance)
                .requestDistributeService(requestDistributeService)
                .concurrentLimitHandler(concurrentLimitHandler)
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
            System.out.println("隧道启动成功, 配置参数=" + System.lineSeparator() + JSON.toJSONString(tunnelInstance, true));
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
