package com.zealsinger.kv.server.Impl;

import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.domain.NoteContent;
import com.zealsinger.kv.dto.*;
import com.zealsinger.kv.enums.ResponseCodeEnum;
import com.zealsinger.kv.repository.NoteContentRepository;
import com.zealsinger.kv.server.NoteContentServer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class NoteContentServerImpl implements NoteContentServer {

    @Resource
    private NoteContentRepository noteContentRepository;
    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        String noteId = addNoteContentReqDTO.getId();
        String content = addNoteContentReqDTO.getContent();
        NoteContent noteContent = NoteContent.builder().id(UUID.fromString(noteId)).content(content).build();
        noteContentRepository.save(noteContent);
        return Response.success();
    }

    @Override
    public Response<?> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        String id = findNoteContentReqDTO.getId();
        Optional<NoteContent> optional = noteContentRepository.findById(UUID.fromString(id));
        // 若笔记内容不存在
        if (optional.isEmpty()) {
            throw new BusinessException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }

        NoteContent noteContentDO = optional.get();
        // 构建返参 DTO
        FindNoteContentRspDTO findNoteContentRspDTO = FindNoteContentRspDTO.builder()
                .id(noteContentDO.getId())
                .content(noteContentDO.getContent())
                .build();

        return Response.success(findNoteContentRspDTO);
    }

    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        String id = deleteNoteContentReqDTO.getId();
        noteContentRepository.deleteById(UUID.fromString(id));
        return Response.success();
    }

    @Override
    public Response<?> updateNoteContent(UpdateNoteContentReqDTO updateNoteContentReqDTO) {
        try{
            String contentUuid = updateNoteContentReqDTO.getContentUuid();
            String content = updateNoteContentReqDTO.getContent();
            noteContentRepository.updateContentByUuid(content, UUID.fromString(contentUuid));
        }catch (Exception e){
            throw new BusinessException(ResponseCodeEnum.SYSTEM_ERROR);
        }
        return Response.success();
    }
}
