package com.zealsinger.count.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.count.domain.entity.NoteCount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteCountMapper extends BaseMapper<NoteCount> {

}