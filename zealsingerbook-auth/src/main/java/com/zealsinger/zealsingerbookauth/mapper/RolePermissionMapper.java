package com.zealsinger.zealsingerbookauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.zealsingerbookauth.domain.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

}