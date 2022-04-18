/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.dataeye.proxy.apn;


import com.dataeye.logback.LogbackRollingFileUtil;
import com.dataeye.proxy.apn.bean.ApnHandlerParams;
import com.dataeye.proxy.apn.bean.RequestMonitor;
import com.dataeye.proxy.apn.handler.ConnectionLimitHandler;
import com.dataeye.proxy.apn.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
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

    @Autowired
    ApnProxyRemoteChooser apnProxyRemoteChooser;
    @Autowired
    RequestDistributeService requestDistributeService;
    @Resource
    TunnelInitMapper tunnelInitMapper;

    /**
     * 初始化隧道实例
     */
    @PostConstruct
    public void initMultiTunnel() {
        // 获取初始化参数
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        // 创建实例
        startByConfig(tunnelInstances);
    }

    /**
     * 根据配置参数启动
     */
    public void startByConfig(List<TunnelInstance> tunnelInstances) {
        int size = tunnelInstances.size();
        ThreadPoolTaskExecutor threadPoolTaskExecutor = getTunnelThreadpool(size, "tunnel_create");
        tunnelInstances.forEach(instance -> threadPoolTaskExecutor.submit(new CreateProxyServerTask(instance)));
        LOG.info("根据配置参数共启动 [{}] 个 proxy server", tunnelInstances.size());
    }

    /**
     * 根据隧道实例个数初始化线程池
     *
     * @param instanceSize proxy server 实例个数
     * @return
     */
    public ThreadPoolTaskExecutor getTunnelThreadpool(int instanceSize, String threadNamePrefix) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(instanceSize);
        pool.setMaxPoolSize(instanceSize);
        pool.setQueueCapacity(2 * instanceSize);
        pool.setKeepAliveSeconds(0);
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setThreadNamePrefix(threadNamePrefix);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.initialize();
        LOG.info("隧道初始化线程池创建完成");
        return pool;
    }

    /**
     * 启动一个 proxy server
     *
     * @param tunnelInstance 隧道实例
     */
    private void startProxyServer(TunnelInstance tunnelInstance) {
        String alias = tunnelInstance.getAlias();
        String host = tunnelInstance.getIp();
        int port = tunnelInstance.getPort();
        int bossThreadSize = tunnelInstance.getBossThreadSize();
        int workerThreadSize = tunnelInstance.getWorkerThreadSize();

        // 初始化业务线程池
        ThreadPoolTaskExecutor businessThreadPool = getTunnelThreadpool(tunnelInstance.getBusinessThreadSize(),
                "tunnel_" + tunnelInstance.getAlias());

        RequestMonitor requestMonitor = new RequestMonitor();

        ApnHandlerParams apnHandlerParams = ApnHandlerParams.builder()
                .apnProxyRemoteChooser(apnProxyRemoteChooser)
                .tunnelInstance(tunnelInstance)
                .requestDistributeService(requestDistributeService)
                .ioThreadPool(businessThreadPool)
                .requestMonitor(requestMonitor)
                .connectionLimitHandler(new ConnectionLimitHandler(tunnelInstance))
                .build();

        EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreadSize);
        EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreadSize);
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .localAddress(host, port)
                .channel(NioServerSocketChannel.class)
//                // 当设置值超过64KB时，需要在绑定到本地端口前设置。该值设置的是由ServerSocketChannel使用accept接受的SocketChannel的接收缓冲区。
//                .option(ChannelOption.SO_RCVBUF, 1024)
                // 服务端接受连接的队列长度，如果队列已满，客户端连接将被拒绝。默认值，Windows为200，其他为128。
//                .option(ChannelOption.SO_BACKLOG, tunnelInstance.getConcurrency())
                .childHandler(new ApnProxyServerChannelInitializer(apnHandlerParams))
                .childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
                // 有数据立即发送
//                .childOption(ChannelOption.TCP_NODELAY, true)
//                .childOption(ChannelOption.SO_KEEPALIVE, false);
//                // TCP_NODELAY就是用于启用或关于Nagle算法。如果要求高实时性，有数据发送时就马上发送，就将该选项设置为true关闭Nagle算法；
//                // 如果要减少发送次数减少网络交互，就设置为false等累积一定大小后再发送。默认为false。
//                .childOption(ChannelOption.TCP_NODELAY, false);
        try {
            ChannelFuture future = serverBootstrap.bind().sync();
            LOG.info("代理服务器 [{}] 启动成功, ip: {}, port: {}", alias, host, port);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            LOG.error("启动代理服务器时，出现异常：{}", e.getMessage());
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
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
            startProxyServer(tunnelInstance);
        }
    }

}
