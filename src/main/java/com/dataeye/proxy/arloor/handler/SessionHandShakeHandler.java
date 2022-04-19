package com.dataeye.proxy.arloor.handler;

import com.alibaba.fastjson.JSON;
import com.dataeye.proxy.apn.cons.Global;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemote;
import com.dataeye.proxy.apn.remotechooser.ApnProxyRemoteChooser;
import com.dataeye.proxy.apn.service.RequestDistributeService;
import com.dataeye.proxy.arloor.bean.ArloorHandlerParams;
import com.dataeye.proxy.arloor.session.Session;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;


public class SessionHandShakeHandler extends SimpleChannelInboundHandler<HttpObject> {
    public static final String NAME = "session";
    private static final Logger log = LoggerFactory.getLogger(SessionHandShakeHandler.class);
    private final Session session;
    private final ArloorHandlerParams arloorHandlerParams;
    private final AtomicBoolean isAllocateIp = new AtomicBoolean(false);

    public SessionHandShakeHandler(Span streamSpan, ArloorHandlerParams arloorHandlerParams) {
        Map<String, String> auths = arloorHandlerParams.getHttpConfig().getAuth();
        Set<String> whiteDomains = arloorHandlerParams.getHttpConfig().getDomainWhiteList();
        this.session = new Session(auths, streamSpan, whiteDomains);
        this.arloorHandlerParams = arloorHandlerParams;
    }

//    @Override
//    public void channelReadComplete(ChannelHandlerContext ctx) {
//        ctx.flush();
//    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 分配ip
        if (!isAllocateIp.get()) {
            ApnProxyRemoteChooser apnProxyRemoteChooser = arloorHandlerParams.getApnProxyRemoteChooser();
            TunnelInstance tunnelInstance = arloorHandlerParams.getTunnelInstance();
            RequestDistributeService requestDistributeService = arloorHandlerParams.getRequestDistributeService();
            ApnProxyRemote apnProxyRemote = apnProxyRemoteChooser.getProxyConfig(tunnelInstance);
            if (Objects.isNull(apnProxyRemote)) {
                requestDistributeService.handleProxyIpIsEmpty(ctx);
            }
            ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).set(apnProxyRemote);
            isAllocateIp.compareAndSet(false, true);
            log.info("初始化分配ip，结果：{}", JSON.toJSONString(apnProxyRemote));
        } else {
            ApnProxyRemote result = ctx.channel().attr(Global.REQUST_IP_ATTRIBUTE_KEY).get();
            log.info("Schema -> 已分配ip，再次提取，结果：{}", JSON.toJSONString(result));
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, HttpObject msg) {
        session.handle(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String clientHostname = ((InetSocketAddress) ctx.channel().remoteAddress()).getHostString();
        log.info("[EXCEPTION][" + clientHostname + "] " + cause.getMessage());
        ctx.close();
    }
}
