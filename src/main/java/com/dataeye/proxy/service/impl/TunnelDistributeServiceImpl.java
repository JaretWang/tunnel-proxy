package com.dataeye.proxy.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dataeye.proxy.bean.IpTimer;
import com.dataeye.proxy.bean.ProxyType;
import com.dataeye.proxy.bean.TunnelAllocateResult;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import com.dataeye.proxy.component.IpSelector;
import com.dataeye.proxy.component.ProxySslContextFactory;
import com.dataeye.proxy.component.TimeCountDown;
import com.dataeye.proxy.config.ProxyServerConfig;
import com.dataeye.proxy.config.TunnelManageConfig;
import com.dataeye.proxy.dao.TunnelInitMapper;
import com.dataeye.proxy.exception.ProxyTypeUnSupportException;
import com.dataeye.proxy.exception.TunnelProxyConfigException;
import com.dataeye.proxy.service.ITunnelDistributeService;
import com.dataeye.proxy.tunnel.TunnelProxyServer;
import com.dataeye.proxy.tunnel.handler.TunnelProxyHandler;
import com.dataeye.proxy.tunnel.handler.TunnelProxyPreHandler;
import com.dataeye.proxy.tunnel.handler.TunnelProxyRelayHandler;
import com.dataeye.proxy.tunnel.initializer.TunnelClientChannelInitializer;
import com.dataeye.proxy.utils.SocksServerUtils;
import com.sun.org.apache.regexp.internal.RE;
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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jaret
 * @date 2022/3/29 11:31
 * @description 隧道分发服务
 */
@Slf4j
@Service
public class TunnelDistributeServiceImpl implements ITunnelDistributeService {

    @Autowired
    private IpSelector ipSelector;
    @Autowired
    private ProxyServerConfig proxyServerConfig;
    @Autowired
    private TunnelManageConfig tunnelManageConfig;
    @Autowired
    private ProxySslContextFactory proxySslContextFactory;
    @Resource(name = "ioThreadPool")
    private ThreadPoolTaskExecutor ioThreadPool;
    @Resource
    TunnelInitMapper tunnelInitMapper;
    @Autowired
    TunnelProxyServer tunnelProxyServer;

    /**
     * 初始化隧道
     */
    @PostConstruct
    @Override
    public void initMultiTunnel() {
        // 获取初始化参数
        List<TunnelInstance> tunnelInstances = tunnelInitMapper.queryAll();
        // 创建实例
        tunnelProxyServer.startByConfig(tunnelInstances);
        // todo 初始化代理ip池 每个server都需要初始化一个ip池，还是说大家公用一个ip池
    }

    /**
     * 检查请代理类型, 直连ip, 独享ip, 隧道ip
     *
     * @param httpRequest 源请求
     * @return
     */
    @Override
    public ProxyType checkType(HttpRequest httpRequest) {
        if (httpRequest.method() == HttpMethod.CONNECT) {
            return ProxyType.tuunel;
        }
        String type = httpRequest.headers().get("my-proxy-type", "");
        if (StringUtils.isBlank(type)) {
            throw new ProxyTypeUnSupportException("请求中不存在 my-proxy-type 选项");
        }
        log.debug("my-proxy-type -> {} ", type);
        return ProxyType.valueOf(type.trim());
    }

    /**
     * 分配代理IP,port，username，password
     *
     * @param httpRequest 源请求
     * @return
     * @throws IOException
     */
    @Override
    public synchronized TunnelAllocateResult getDistributeParams(HttpRequest httpRequest) throws IOException {
        ProxyType proxyType = checkType(httpRequest);
        // 分配ip
        ConcurrentHashMap<ProxyType, List<IpTimer>> distributeList = ipSelector.getDistributeList();
        boolean contains = distributeList.containsKey(proxyType);
        if (!contains) {
            throw new ProxyTypeUnSupportException("代理类型" + proxyType + "不支持");
        }
        List<IpTimer> ipTimers = distributeList.get(proxyType);
        // 临界阈值
        double loadFactor = tunnelManageConfig.getLoadFactor();
        int thresholdValue = (int) (loadFactor * ipTimers.size());
        for (IpTimer ipTimer : ipTimers) {
            // 取一个
            TimeCountDown timeCountDown = ipTimer.getTimeCountDown();
            // 有效期
            if (timeCountDown.getDuration() > 0) {
                String ip = ipTimer.getIp();
                int port = ipTimer.getPort();
                TunnelAllocateResult.TunnelAllocateResultBuilder allocateResultBuilder = TunnelAllocateResult.builder().ip(ip).port(port);
                if (proxyType == ProxyType.tuunel) {
                    String proxyUserName = proxyServerConfig.getProxyUserName();
                    String proxyPassword = proxyServerConfig.getProxyPassword();
                    allocateResultBuilder.username(proxyUserName).password(proxyPassword);
                }
                TunnelAllocateResult tunnelAllocateResult = allocateResultBuilder.build();
                // 检测剩余代理ip池数量
                // 当代理ip的数量, 低于负载因子的个数,再放一批进去
                if (ipTimers.size() <= thresholdValue) {
                    log.warn("{} 代理ip池的存活数量低于阈值 {}, 即将进行补充", proxyType, thresholdValue);
                    List<IpTimer> proIpList = ipSelector.getDistributeList().get(proxyType);
                    String ipAccessLink = ipSelector.getAccessLink().get(proxyType);
                    ipSelector.initList(proIpList, ipAccessLink);
                }
                return tunnelAllocateResult;
            } else {
                // 失效了,移除
                ipTimers.remove(ipTimer);
            }
        }
        List<IpTimer> proxyIpList = ipSelector.getDistributeList().get(proxyType);
        String ipAccessLink = ipSelector.getAccessLink().get(proxyType);
        ipSelector.initList(proxyIpList, ipAccessLink);
        log.error("{} 代理ip池全部失效, 重新初始化, 代理ip个数: {}", proxyType, proxyIpList.size());
        return getDistributeParams(httpRequest);
    }

    @Override
    public void sendProxyRequest(ChannelHandlerContext ctx, HttpRequest httpRequest, TunnelInstance tunnelInstance) throws IOException {
//        // 隧道分配结果
//        TunnelAllocateResult allocateResult = getDistributeParams(httpRequest);
        // todo 芝麻代理套餐有限，不利于测试，现在使用快代理测试，后续记得换回到上面的那个
        log.warn("芝麻代理套餐有限，不利于测试，现在使用快代理测试，后续记得换回到上面的那个");
        TunnelAllocateResult allocateResult = TunnelAllocateResult.builder()
                .ip(proxyServerConfig.getRemoteHost()).port(proxyServerConfig.getRemotePort())
                .username(proxyServerConfig.getProxyUserName()).password(proxyServerConfig.getProxyPassword()).build();

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
            log.warn("代理商地址:{}, 端口：{}, 监听类型：{}", remoteHost, remotePort, proxyServerConfig.getRemoteListenType());

            Channel uaChannel = ctx.channel();
            Bootstrap bootstrap = new Bootstrap()
                    .group(uaChannel.eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tunnelManageConfig.getConnectTimeoutMillis())
                    .option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new TunnelClientChannelInitializer(proxyServerConfig, uaChannel, proxySslContextFactory));
            // 发起连接
            log.info("请求分发 -> 连接代理地址：{}:{}", remoteHost, remotePort);
            bootstrap.connect(remoteHost, remotePort)
                    .addListener((ChannelFutureListener) future1 -> {
                        if (future1.isSuccess()) {
                            // successfully connect to the original server，send connect success msg to UA
                            log.info("业务线程连接成功，地址：{}:{}", remoteHost, remotePort);
                            doAfterConnectSuccess(ctx, future1, httpRequest);
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
    void doAfterConnectSuccess(ChannelHandlerContext ctx, ChannelFuture future1, HttpRequest httpRequest) {
        if (proxyServerConfig.isAppleyRemoteRule()) {
            log.debug("业务线程连接成功之后的操作............");
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

            String data = constructConnectRequestForProxy(httpRequest, proxyServerConfig);
            ByteBuf content = Unpooled.copiedBuffer(data, CharsetUtil.UTF_8);
            future1.channel()
                    .writeAndFlush(content)
                    .addListener((ChannelFutureListener) future2 -> {
                        if (!future2.channel().config().getOption(ChannelOption.AUTO_READ)) {
                            future2.channel().read();
                        }
                    });

        } else {
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
     * @param proxyServerConfig
     * @return
     */
    private String constructConnectRequestForProxy(HttpRequest httpRequest,
                                                   ProxyServerConfig proxyServerConfig) {
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

        if (StringUtils.isNotBlank(proxyServerConfig.getProxyUserName())
                && StringUtils.isNotBlank(proxyServerConfig.getProxyPassword())) {
            // todo 优化 credentials
            String proxyAuthorization = proxyServerConfig.getProxyUserName() + ":"
                    + proxyServerConfig.getProxyPassword();
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
