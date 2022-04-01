package com.dataeye.proxy.service.impl;

import com.dataeye.proxy.bean.IpTimer;
import com.dataeye.proxy.bean.ProxyType;
import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.bean.TunnelProxyListenType;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.component.TimeCountDown;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.cons.HandlerCons;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.service.ProxyService;
import com.dataeye.proxy.tunnel.TunnelProxyServer;
import com.dataeye.proxy.tunnel.handler.TunnelProxyHandler;
import com.dataeye.proxy.tunnel.handler.TunnelProxyPreHandler;
import com.dataeye.proxy.tunnel.handler.TunnelProxyRelayHandler;
import com.dataeye.proxy.tunnel.initializer.TunnelClientChannelInitializer;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;

/**
 * @author jaret
 * @date 2022/3/29 11:31
 * @description 隧道分发服务
 */
@Slf4j
@Service
public class TunnelDistributeServiceImpl implements ITunnelDistributeService {

    @Autowired
    private ProxyServerConfig proxyServerConfig;
    @Autowired
    private ProxySslContextFactory proxySslContextFactory;
    @Resource(name = "ioThreadPool")
    private ThreadPoolTaskExecutor ioThreadPool;
    @Resource
    TunnelInitMapper tunnelInitMapper;
    @Resource
    TunnelProxyServer tunnelProxyServer;
    @Resource
    IpSelector ipSelector;
    @Autowired
    private ProxyService proxyService;

    /**
     * 初始化隧道实例
     */
    @PostConstruct
    @Override
    public void initMultiTunnel() {
        // 获取初始化参数
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        // 创建实例
        tunnelProxyServer.startByConfig(tunnelInstances);
    }

    @Override
    public TunnelAllocateResult getDistributeParams(HttpRequest httpRequest, TunnelInstance tunnelInstance) throws IOException {
        log.info("开始分配代理IP（暂时使用芝麻代理）");
        String proxyServer = tunnelInstance.toString();
        // 分配ip
        List<IpTimer> ipTimers = ipSelector.getScheduleProxyIpPool().get(proxyServer);
        if (ObjectUtils.isEmpty(ipTimers)) {
            log.warn("实例 {} 对应的代理IP列表为空，需要重新加载", tunnelInstance.getAlias());
            ipSelector.changeIpForZhiMa(tunnelInstance);
            return getDistributeParams(httpRequest, tunnelInstance);
        }
        // 分配ip
        IpTimer ipTimer = ipTimers.get(0);
        String ip = ipTimer.getIp();
        int port = ipTimer.getPort();
        return TunnelAllocateResult.builder().tunnelProxyListenType(TunnelProxyListenType.PLAIN).proxyType(ProxyType.exclusiveTunnel)
                .ip(ip).port(port).build();
    }

    /**
     * 分配代理IP,port，username，password
     *
     * @param httpRequest 源请求
     * @return
     */
//    @Override
    public TunnelAllocateResult getDistributeParams1(HttpRequest httpRequest, TunnelInstance tunnelInstance) throws IOException {
        log.info("开始分配代理IP");
        String ipAccessLink = proxyServerConfig.getDirectIpAccessLink();
//        double loadFactor = tunnelManageConfig.getLoadFactor();
        String proxyServer = tunnelInstance.toString();
        // 分配ip
        List<IpTimer> ipTimers = ipSelector.getScheduleProxyIpPool().get(proxyServer);
        if (ObjectUtils.isEmpty(ipTimers)) {
            log.warn("实例 {} 对应的代理IP列表为空，需要重新加载", tunnelInstance.getAlias());
            ipSelector.initIpForSingleProxyServer(proxyServer, ipAccessLink);
            return getDistributeParams1(httpRequest, tunnelInstance);
        }

        for (IpTimer ipTimer : ipTimers) {
            // ip倒计时器
            TimeCountDown timeCountDown = ipTimer.getTimeCountDown();
            // 有效期
            if (timeCountDown.isEffective()) {
                String ip = ipTimer.getIp();
                int port = ipTimer.getPort();
                // 设置引用计数
                int referenceCount = ipTimer.getReferenceCount().incrementAndGet();
                log.info("代理IP分配结果, ip={},port={}, 引用次数={}", ip, port, referenceCount);
                return TunnelAllocateResult.builder().ip(ip).port(port).build();
            } else {
                // 失效了,移除
                log.warn("ip失效了,定时器时间：{}, 移除ip: {}", timeCountDown.getDuration(), ipTimer.getIp());
                ipTimers.remove(ipTimer);
            }
        }
        log.warn("实例 [{}] 代理ip池全部失效, 重新初始化, 代理ip个数: {}", proxyServer, ipSelector.getScheduleProxyIpPool().get(proxyServer).size());
        ipSelector.initIpForSingleProxyServer(proxyServer, ipAccessLink);
        return getDistributeParams1(httpRequest, tunnelInstance);
    }

    @Override
    public void sendHttpProxyRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) throws IOException {

    }

    @Override
    public void sendTunnelProxyRequest(ChannelHandlerContext ctx, HttpRequest httpRequest,
                                 TunnelInstance tunnelInstance,  ProxyService proxyService) throws IOException {
        // 隧道分配结果
        TunnelAllocateResult allocateResult = getDistributeParams(httpRequest, tunnelInstance);

//        TunnelAllocateResult allocateResult = TunnelAllocateResult.builder()
//                .tunnelProxyListenType(TunnelProxyListenType.PLAIN).proxyType(ProxyType.exclusiveTunnel)
//                .ip(HandlerCons.ip).port(HandlerCons.port).build();


//        ProxyCfg proxyCfg = proxyService.getOne().get();
//        log.warn("云代理");
//        TunnelAllocateResult allocateResult = TunnelAllocateResult.builder()
//                .tunnelProxyListenType(TunnelProxyListenType.PLAIN).proxyType(ProxyType.exclusiveTunnel)
//                .ip(proxyCfg.getHost()).port(proxyCfg.getPort())
//                .username(proxyCfg.getUserName()).password(proxyCfg.getPassword()).build();


//        // todo 芝麻代理套餐有限，不利于测试，现在使用快代理测试，后续记得换回到上面的那个
//        log.warn("芝麻代理套餐有限，不利于测试，现在使用快代理测试，后续记得换回到上面的那个");
//        TunnelAllocateResult allocateResult = TunnelAllocateResult.builder()
//                .ip(proxyServerConfig.getRemoteHost()).port(proxyServerConfig.getRemotePort())
//                .username(proxyServerConfig.getProxyUserName()).password(proxyServerConfig.getProxyPassword()).build();

        // 提交代理请求任务
        ProxyRequestTask proxyRequestTask = new ProxyRequestTask(ctx, httpRequest, allocateResult);
        ioThreadPool.submit(proxyRequestTask);
        log.info("提交了一个任务，参数：{}", allocateResult);
    }

    /**
     * 代理请求任务
     */
    class ProxyRequestTask implements Runnable {

        private final ChannelHandlerContext ctx;
        private final HttpRequest httpRequest;
        private final TunnelAllocateResult allocateResult;

        public ProxyRequestTask(ChannelHandlerContext ctx, HttpRequest httpRequest, TunnelAllocateResult allocateResult) {
            this.ctx = ctx;
            this.httpRequest = httpRequest;
            this.allocateResult = allocateResult;
        }

        @Override
        public void run() {
            String remoteHost = allocateResult.getIp();
            int remotePort = allocateResult.getPort();
            log.warn("代理商地址:{}, 端口：{}, 监听类型：{}", remoteHost, remotePort, allocateResult.getTunnelProxyListenType());

            Channel uaChannel = ctx.channel();
            Bootstrap bootstrap = new Bootstrap()
                    .group(uaChannel.eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new TunnelClientChannelInitializer(allocateResult, uaChannel, proxySslContextFactory));
            // 发起连接
            log.info("请求分发 -> 连接代理地址：{}:{}", remoteHost, remotePort);
            bootstrap.connect(remoteHost, remotePort)
                    .addListener((ChannelFutureListener) future1 -> {
                        if (future1.isSuccess()) {
                            // successfully connect to the original server，send connect success msg to UA
                            log.info("业务线程连接成功，地址：{}:{}", remoteHost, remotePort);
                            doAfterConnectSuccess(ctx, future1, httpRequest, allocateResult);
                        } else {
                            log.info("业务线程连接失败，地址：{}:{}", remoteHost, remotePort);
                            SocksServerUtils.closeOnFlush(ctx.channel());
                        }
                    });
        }
    }

    /**
     * 连接成功之后的操作
     *
     * @param ctx
     * @param future1
     * @param httpRequest
     */
    void doAfterConnectSuccess(ChannelHandlerContext ctx, ChannelFuture future1,
                                HttpRequest httpRequest,TunnelAllocateResult allocateResult) {
        if (proxyServerConfig.isAppleyRemoteRule()) {
            log.info("开始转播数据");
            //todo 由于放在了一个线程里面，好像之前的handler不存在了
//            ctx.pipeline().remove("codec");
//            ctx.pipeline().remove(TunnelProxyPreHandler.HANDLER_NAME);
//            ctx.pipeline().remove(TunnelProxyHandler.HANDLER_NAME);

            ctx.pipeline().remove(HttpServerCodec.class);
            ctx.pipeline().remove(HttpObjectAggregator.class);
            ctx.pipeline().remove(ChunkedWriteHandler.class);
//            ctx.pipeline().remove(TunnelProxyPreHandler.class);
            ctx.pipeline().remove(TunnelProxyHandler.class);

            // add relay handler
            ctx.pipeline().addLast(new TunnelProxyRelayHandler("UA --> Remote", future1.channel()));

            String data = constructConnectRequestForProxy(httpRequest, allocateResult);
            ByteBuf content = Unpooled.copiedBuffer(data, CharsetUtil.UTF_8);
            future1.channel()
                    .writeAndFlush(content)
                    .addListener((ChannelFutureListener) future2 -> {
                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                            future2.channel().read();
                        }
                    });

        } else {
            log.warn("取消使用代理商地址访问");
            HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "Connection established"));
            ctx.writeAndFlush(proxyConnectSuccessResponse).addListener(
                    (ChannelFutureListener) future2 -> {
                        // remove handlers
                        ctx.pipeline().remove("codec");
                        ctx.pipeline().remove(TunnelProxyPreHandler.HANDLER_NAME);
                        ctx.pipeline().remove(TunnelProxyHandler.HANDLER_NAME);

                        // add relay handler
                        String tag = "UA --> " + proxyServerConfig.getRemote();
                        ctx.pipeline().addLast(new TunnelProxyRelayHandler(tag, future1.channel()));
                    });
        }
    }

    /**
     * 构造代理链接请求
     *
     * @param httpRequest
     * @param allocateResult
     * @return
     */
    private String constructConnectRequestForProxy(HttpRequest httpRequest,
                                                   TunnelAllocateResult allocateResult) {
        System.out.println("constructConnectRequestForProxy....");
        String CRLF = "\r\n";
        String url = httpRequest.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append(httpRequest.getMethod().name()).append(" ").append(url).append(" ")
                .append(httpRequest.getProtocolVersion().text()).append(CRLF);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
                continue;
            }

            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
                continue;
            }

            for (String headerValue : httpRequest.headers().getAll(headerName)) {
                sb.append(headerName).append(": ").append(headerValue).append(CRLF);
            }
        }

        String username = allocateResult.getUsername();
        String password = allocateResult.getPassword();
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            // todo 优化 credentials
            String proxyAuthorization = username + ":" + password;
            try {
                sb.append(
                        "Proxy-Authorization: Basic "
                                + Base64.encodeBase64String(proxyAuthorization.getBytes("UTF-8")))
                        .append(CRLF);
            } catch (UnsupportedEncodingException e) {
            }

        }

        sb.append(CRLF);

        return sb.toString();
    }

}
