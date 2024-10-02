package com.zealsinger.kv.server.Impl;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.domain.NoteContent;
import com.zealsinger.kv.dto.AddNoteContentReqDTO;
import com.zealsinger.kv.repository.NoteContentRepository;
import com.zealsinger.kv.server.NoteContentServer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NoteContentServerImpl implements NoteContentServer {

    @Resource
    private NoteContentRepository noteContentRepository;
    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        Long noteId = addNoteContentReqDTO.getId();
        String content = addNoteContentReqDTO.getContent();
        NoteContent noteContent = NoteContent.builder().id(UUID.randomUUID()).content(content).build();
        noteContentRepository.save(noteContent);
        return Response.success();
    }
}
