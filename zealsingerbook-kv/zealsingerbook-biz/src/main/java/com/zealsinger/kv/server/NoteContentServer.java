package com.zealsinger.kv.server;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.dto.AddNoteContentReqDTO;

public interface NoteContentServer {
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);
}
