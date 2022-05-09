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
     * 查询所有的隧道实例
     *
     * @return 隧道实例列表
     */
    List<TunnelInstance> queryAll();

}
