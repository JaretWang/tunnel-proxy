package com.dataeye.proxy.dao;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataeye.proxy.bean.dto.VpsInstance;
import org.apache.ibatis.annotations.Param;

/**
 * (VpsInstance)表数据库访问层
 *
 * @author makejava
 * @since 2022-11-07 17:27:56
 */
public interface VpsInstanceMapper extends BaseMapper<VpsInstance> {

    /**
     * 批量新增数据（MyBatis原生foreach方法）
     *
     * @param entities List<VpsInstance> 实例对象列表
     * @return 影响行数
     */
    int insertBatch(@Param("entities") List<VpsInstance> entities);

    /**
     * 批量新增或按主键更新数据（MyBatis原生foreach方法）
     *
     * @param entities List<VpsInstance> 实例对象列表
     * @return 影响行数
     * @throws org.springframework.jdbc.BadSqlGrammarException 入参是空List的时候会抛SQL语句错误的异常，请自行校验入参
     */
    int insertOrUpdateBatch(@Param("entities") List<VpsInstance> entities);

}

