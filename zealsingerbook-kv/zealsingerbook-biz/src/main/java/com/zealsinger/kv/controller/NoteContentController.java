package com.zealsinger.kv.controller;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.dto.AddNoteContentReqDTO;
import com.zealsinger.kv.dto.DeleteNoteContentReqDTO;
import com.zealsinger.kv.dto.FindNoteContentReqDTO;
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

    @PostMapping("/NoteContent/add")
    public Response<?> addNoteContent(@RequestBody @Validated AddNoteContentReqDTO addNoteContentReqDTO) {
        return noteContentService.addNoteContent(addNoteContentReqDTO);
    }

    @PostMapping("/NoteContent/find")
    public Response<?> findNoteContent(@RequestBody @Validated FindNoteContentReqDTO findNoteContentReqDTO){
        return noteContentService.findNoteContent(findNoteContentReqDTO);
    }

    @PostMapping("/NoteContent/delete")
    public Response<?>  deleteNoteContent(@RequestBody @Validated DeleteNoteContentReqDTO deleteNoteContentReqDTO){
        return noteContentService.deleteNoteContent(deleteNoteContentReqDTO);
    }
}
