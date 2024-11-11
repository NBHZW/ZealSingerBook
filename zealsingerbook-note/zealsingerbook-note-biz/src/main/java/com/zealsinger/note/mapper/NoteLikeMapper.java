package com.zealsinger.note.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.note.domain.entity.NoteLike;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteLikeMapper extends BaseMapper<NoteLike> {
}
