package com.zealsinger.note.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.note.domain.entity.Note;
import com.zealsinger.note.domain.vo.PublishNoteReqVO;

public interface NoteServer extends IService<Note> {
    Response<?> publicNote(PublishNoteReqVO publishNoteReqVO);
}
