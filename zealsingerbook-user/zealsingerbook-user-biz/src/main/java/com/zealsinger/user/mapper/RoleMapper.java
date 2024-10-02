package com.zealsinger.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.user.domain.entity.Role;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    /**
     * 查询所有被启用的角色
     *
     * @return
     */
    List<Role> selectEnabledList();
}