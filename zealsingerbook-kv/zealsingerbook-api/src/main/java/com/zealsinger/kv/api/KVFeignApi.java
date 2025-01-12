package com.zealsinger.kv.api;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.constanst.ApiConstants;
import com.zealsinger.kv.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(ApiConstants.SERVICE_NAME)
public interface KVFeignApi {
    String PREFIX = "/kv";

    @PostMapping(value = PREFIX + "/NoteContent/add")
    Response<?> addNoteContent(@RequestBody @Validated AddNoteContentReqDTO addNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/NoteContent/find")
    Response<FindNoteContentRspDTO> findNoteContent(@Validated @RequestBody FindNoteContentReqDTO findeNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/NoteContent/delete")
    Response<?> deleteNoteContent(@RequestBody @Validated DeleteNoteContentReqDTO deleteNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/NoteContent/update")
    Response<?> updateNoteContent(@RequestBody @Validated UpdateNoteContentReqDTO updateNoteContentReqDTO);

    @PostMapping(value = PREFIX+"/comment/content/batchAdd")
    Response<?> batchAddCommentContent(@Validated @RequestBody BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);
}
