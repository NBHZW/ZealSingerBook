package com.zealsinger.note.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.note.domain.dto.DeleteNoteByIdReqDTO;
import com.zealsinger.note.domain.dto.FindNoteByIdReqDTO;
import com.zealsinger.note.domain.dto.UpdateTopStatusDTO;
import com.zealsinger.note.domain.dto.UpdateVisibleOnlyMeReqVO;
import com.zealsinger.note.domain.vo.*;
import com.zealsinger.note.server.NoteServer;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/note")
public class NoteController {

    @Resource
    private NoteServer noteServer;

    @PostMapping("/publish")
    @ZealLog(description = "发布笔记")
    public Response<?> publicNote(@RequestBody @Validated PublishNoteReqVO publishNoteReqVO){
        return noteServer.publicNote(publishNoteReqVO);
    }

    @PostMapping("/findById")
    public Response<?> findById(@Validated @RequestBody FindNoteByIdReqDTO findNoteByIdReqDTO) throws ExecutionException, InterruptedException {
        return noteServer.findById(findNoteByIdReqDTO);
    }

    @PostMapping("/update")
    public Response<?> updateNote(@Validated @RequestBody UpdateNoteReqVO reqVO){
        return noteServer.updateNote(reqVO);
    }

    @PostMapping("/delete")
    public Response<?> deleteNote(@Validated @RequestBody DeleteNoteByIdReqDTO deleteNoteByIdReqDTO){
        return noteServer.deleteNote(deleteNoteByIdReqDTO);
    }

    @PostMapping("/visibleOnlyMe")
    public Response<?> visibleOnlyMe(@Validated @RequestBody UpdateVisibleOnlyMeReqVO updateVisibleOnlyMeReqVO){
        return noteServer.visibleOnlyMe(updateVisibleOnlyMeReqVO);
    }

    @PostMapping("/top")
    public Response<?> updateTopStatus(@Validated @RequestBody UpdateTopStatusDTO updateTopStatusDTO){
        return noteServer.updateTopStatus(updateTopStatusDTO);
    }

    @PostMapping("/like")
    public Response<?> likeNote(@RequestBody @Validated LikeNoteReqVO likeNoteReqVO){
        return noteServer.likeNote(likeNoteReqVO);
    }

    @PostMapping("/unlike")
    public Response<?> unlikeNote(@RequestBody @Validated UnlikeNoteReqVO unlikeNoteReqVO){
        return noteServer.unlikeNote(unlikeNoteReqVO);
    }

    @PostMapping("/collect")
    public Response<?> collectNote(@RequestBody @Validated CollectNoteReqVO collectNoteReqVO){
        return noteServer.collectNote(collectNoteReqVO);
    }

    @PostMapping("/uncollect")
    public Response<?> uncollectNote(@RequestBody @Validated UnCollectNoteReqVO unCollectNoteReqVO){
        return noteServer.uncollectNote(unCollectNoteReqVO);
    }
}
