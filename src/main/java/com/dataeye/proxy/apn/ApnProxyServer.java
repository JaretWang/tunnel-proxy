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

import com.dataeye.proxy.TunnelProxyApplication;
import com.dataeye.proxy.apn.config.ApnProxyConfig;
import com.dataeye.proxy.apn.config.ApnProxyConfigReader;
import com.dataeye.proxy.apn.config.ApnProxyRemoteRulesConfigReader;
import com.dataeye.proxy.apn.initializer.ApnProxyServerChannelInitializer;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.service.ProxyService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author xmx
 * @version $Id: com.dataeye.proxy.apn.ApnProxyServer 14-1-8 16:13 (xmx) Exp $
 */
@Component
public class ApnProxyServer {

    private static final Logger LOG = LoggerFactory.getLogger(ApnProxyServer.class);

    @Autowired
    ApnProxyRemoteChooser apnProxyRemoteChooser;
    @Autowired
    ProxyServerConfig proxyServerConfig;
    @Autowired
    ProxySslContextFactory proxySslContextFactory;
    @Autowired
    IpSelector ipSelector;
    @Autowired
    ProxyService proxyService;
    @Autowired
    ITunnelDistributeService tunnelDistributeService;
    @Resource
    TunnelInitMapper tunnelInitMapper;
//    EventLoopGroup bossGroup;
//    EventLoopGroup workerGroup;

    /**
     * 初始化隧道实例
     */
    @PostConstruct
    public void initMultiTunnel() {
        ApnProxyConfigReader apnProxyConfigReader = new ApnProxyConfigReader();
        apnProxyConfigReader.read(TunnelProxyApplication.class
                .getResourceAsStream("/plain-proxy-config.xml"));

        ApnProxyRemoteRulesConfigReader apnProxyRemoteRulesConfigReader = new ApnProxyRemoteRulesConfigReader();
        apnProxyRemoteRulesConfigReader.read(TunnelProxyApplication.class
                .getResourceAsStream("/plain-proxy-config.xml"));

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
        ThreadPoolTaskExecutor threadPoolTaskExecutor = getTunnelThreadpool(size);
        tunnelInstances.forEach(instance -> threadPoolTaskExecutor.submit(new CreateProxyServerTask(instance)));
        LOG.info("根据配置参数共启动 [{}] 个 proxy server", tunnelInstances.size());
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
        LOG.info("隧道初始化线程池创建完成");
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
            startProxyServer(tunnelInstance);
        }
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

        EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreadSize);
        EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreadSize);
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .localAddress(host, port)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ApnProxyServerChannelInitializer(apnProxyRemoteChooser, tunnelInstance))
                .childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
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

}
