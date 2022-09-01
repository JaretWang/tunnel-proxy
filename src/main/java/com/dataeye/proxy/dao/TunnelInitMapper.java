package com.dataeye.proxy.dao;

import com.dataeye.proxy.bean.CustomIpAllocate;
import com.dataeye.proxy.bean.TunnelMonitorLog;
import com.dataeye.proxy.bean.dto.TunnelInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author jaret
 * @date 2022/3/30 11:32
 * @description
 */
@Mapper
public interface TunnelInitMapper {

    /**
     * 查询定制ip分配情况
     *
     * @param ip
     * @param port
     * @return
     */
    CustomIpAllocate queryCustomIpAllocate(@Param("ip") String ip, @Param("port") int port);

    /**
     * 查询所有的隧道实例
     *
     * @return 隧道实例列表
     */
    List<TunnelInstance> queryAll();

    /**
     * 更新隧道参数
     *
     * @param tunnelInstance
     * @return
     */
    int updateTunnel(TunnelInstance tunnelInstance);

    /**
     * 更新已拉取ip数
     *
     * @param usedIp
     * @return
     */
    int updateUsedIp(@Param("alias") String alias, @Param("usedIp") int usedIp);

    /**
     * 更新成功率
     *
     * @param alias
     * @param rate
     * @param useTimes
     * @return
     */
    int updateSuccessRate(@Param("alias") String alias, @Param("rate") int rate, @Param("useTimes") int useTimes);

    /**
     * 添加监控日志
     *
     * @param tunnelMonitorLog
     * @return
     */
    int addMonitorLog(TunnelMonitorLog tunnelMonitorLog);

}
