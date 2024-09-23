package com.zealsinger.zealsingerbookauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.zealsingerbookauth.domain.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
    /**
     * 查询 APP 端所有被启用的权限
     *
     * @return
     */
    List<Permission> selectAppEnabledList();
}