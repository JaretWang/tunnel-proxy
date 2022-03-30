package com.dataeye.proxy.dao;

import com.dataeye.proxy.bean.dto.TunnelInstance;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author jaret
 * @date 2022/3/30 11:32
 * @description
 */
@Mapper
public interface TunnelInitMapper {

    /**
     * 查询所有的IP隧道的配置参数
     * @return
     */
//    @Select("${select * from tunnel_manage}")
    List<TunnelInstance> queryAll();

}
