package com.zealsinger.note.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.note.domain.vo.PublishNoteReqVO;
import com.zealsinger.note.server.NoteServer;
import jakarta.annotation.Resource;
import org.checkerframework.common.util.report.qual.ReportUse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
