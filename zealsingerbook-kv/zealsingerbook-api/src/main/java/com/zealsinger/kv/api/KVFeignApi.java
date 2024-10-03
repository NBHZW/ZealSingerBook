package com.zealsinger.kv.api;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.constanst.ApiConstants;
import com.zealsinger.kv.dto.AddNoteContentReqDTO;
import com.zealsinger.kv.dto.DeleteNoteContentReqDTO;
import com.zealsinger.kv.dto.FindNoteContentReqDTO;
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
    Response<?> findNoteContent(@Validated @RequestBody FindNoteContentReqDTO findeNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/NoteContent/delete")
    Response<?> deleteNoteContent(@RequestBody @Validated DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
