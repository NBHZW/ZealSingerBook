package com.zealsinger.kv.controller;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.dto.AddNoteContentReqDTO;
import com.zealsinger.kv.server.NoteContentServer;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/kv")
@RestController
public class NoteContentController {
    @Resource
    private NoteContentServer noteContentService;

    @PostMapping("/addNoteContent")
    public Response<?> addNoteContent(@RequestBody @Validated AddNoteContentReqDTO addNoteContentReqDTO) {
        return noteContentService.addNoteContent(addNoteContentReqDTO);
    }
}
