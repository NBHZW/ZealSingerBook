package com.zealsinger.note.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.note.domain.dto.FindNoteByIdReqDTO;
import com.zealsinger.note.domain.entity.Note;
import com.zealsinger.note.domain.vo.PublishNoteReqVO;
import com.zealsinger.note.domain.vo.UpdateNoteReqVO;

import java.util.concurrent.ExecutionException;

public interface NoteServer extends IService<Note> {
    Response<?> publicNote(PublishNoteReqVO publishNoteReqVO);

    Response<?> findById(FindNoteByIdReqDTO findNoteByIdReqDTO) throws ExecutionException, InterruptedException;

    Response<?> updateNote(UpdateNoteReqVO reqVO);
}
