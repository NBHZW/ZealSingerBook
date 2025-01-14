package com.zealsinger.count.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.count.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

}