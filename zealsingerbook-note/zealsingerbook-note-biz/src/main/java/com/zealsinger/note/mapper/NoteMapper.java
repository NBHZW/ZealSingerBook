package com.zealsinger.note.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zealsinger.note.domain.entity.Note;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteMapper extends BaseMapper<Note> {

}