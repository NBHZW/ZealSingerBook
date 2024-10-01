package com.zealsinger.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.user.domain.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}