package com.zealsinger.kv.api;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.constanst.ApiConstants;
import com.zealsinger.kv.dto.AddNoteContentReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(ApiConstants.SERVICE_NAME)
public interface KVFeignApi {
    String PREFIX = "/kv";

    @PostMapping(value = PREFIX + "/addNoteContent")
    Response<?> addNoteContent(@RequestBody @Validated AddNoteContentReqDTO addNoteContentReqDTO);
}
