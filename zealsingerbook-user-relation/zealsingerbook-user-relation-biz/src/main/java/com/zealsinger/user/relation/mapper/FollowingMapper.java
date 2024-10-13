package com.zealsinger.user.relation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.user.relation.domain.entity.Following;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FollowingMapper extends BaseMapper<Following> {
}