package com.zealsinger.kv.server;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.dto.AddNoteContentReqDTO;
import com.zealsinger.kv.dto.DeleteNoteContentReqDTO;
import com.zealsinger.kv.dto.FindNoteContentReqDTO;

public interface NoteContentServer {
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

    Response<?> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO);

    Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
