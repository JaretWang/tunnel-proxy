package com.dataeye.proxy.server.handler;

import com.dataeye.proxy.bean.ApnHandlerParams;
import com.dataeye.proxy.bean.ProxyIp;
import com.dataeye.proxy.bean.RequestMonitor;
import com.dataeye.proxy.cons.GlobalParams;
import com.dataeye.proxy.monitor.IpMonitorUtils;
import com.dataeye.proxy.monitor.ReqMonitorUtils;
import com.dataeye.proxy.server.service.RequestDistributeService;
import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import com.dataeye.proxy.utils.SocksServerUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * @author jaret
 * @date 2022/4/14 10:41
 */
public class ApnProxyTunnelHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.tunnel";
    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");
    private final RequestDistributeService requestDistributeService;
    private final ApnHandlerParams apnHandlerParams;

    public ApnProxyTunnelHandler(ApnHandlerParams apnHandlerParams) {
        this.requestDistributeService = apnHandlerParams.getRequestDistributeService();
        this.apnHandlerParams = apnHandlerParams;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("tunnel channelActive");
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        logger.debug("tunnel channelRead");
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        try {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
                logger.debug("tunnel 接收请求, 请求行和请求头: {}", fullHttpRequest.toString());
                ProxyIp proxyIp = GlobalParams.getProxyIp(ctx);
                if (Objects.isNull(proxyIp)) {
                    throw new RuntimeException("tunnel 获取缓存ip为空");
                }
                logger.debug("转发 CONNECT 请求, proxy={}, srcIp={}, method={}, uri={}",
                        proxyIp.getRemote(), SocksServerUtils.getReqSrcIp(ctx), requestMonitor.getMethod(), requestMonitor.getUri());

                requestDistributeService.sendHttps(ctx, fullHttpRequest, apnHandlerParams, proxyIp);
//                requestDistributeService.sendConnectReqByNettyClient(requestMonitor, ctx, fullHttpRequest, proxyIp, apnHandlerParams.getTunnelInstance());
            }
        } catch (Exception e) {
            logger.error("tunnel异常, 关闭通道, srcIp={}, method={}, uri={}, cause={}",
                    SocksServerUtils.getReqSrcIp(ctx), requestMonitor.getMethod(), requestMonitor.getUri(), e.getMessage(), e);
            SocksServerUtils.errorHttpResp(ctx.channel(), e.getMessage());
            SocksServerUtils.closeOnFlush(ctx.channel());
        } finally {
            // 这里的 msg 释放引用就是对 fullHttpRequest 释放引用
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("tunnel channelInactive");
        super.channelInactive(ctx);
        ctx.close();
        ProxyIp.removeConnect(GlobalParams.getProxyIp(ctx));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        RequestMonitor requestMonitor = apnHandlerParams.getRequestMonitor();
        logger.error("tunnel exceptionCaught, srcIp={}, method={}, uri={}, targetAddr={}, cause={}",
                SocksServerUtils.getReqSrcIp(ctx), requestMonitor.getMethod(), requestMonitor.getUri(), requestMonitor.getUri(), cause.getMessage(), cause);
        ReqMonitorUtils.error(requestMonitor, HANDLER_NAME, cause.getMessage());
        IpMonitorUtils.error(requestMonitor, HANDLER_NAME, cause.getMessage());
        ctx.close();
        ProxyIp.removeConnect(GlobalParams.getProxyIp(ctx));
    }

}
