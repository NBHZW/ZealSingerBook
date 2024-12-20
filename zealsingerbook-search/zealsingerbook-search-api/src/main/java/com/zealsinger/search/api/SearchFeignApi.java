package com.zealsinger.search.api;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.search.constant.ApiConstant;
import com.zealsinger.search.dto.RebuildNoteDocumentReqDTO;
import com.zealsinger.search.dto.RebuildUserDocumentReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstant.SERVICE_NAME)
public interface SearchFeignApi {
    String PREFIX = "/search";

    @PostMapping(value = PREFIX+"/note/document/rebuild")
    Response<?> rebuildNoteDocument(@Validated @RequestBody RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);

    /**
     * 重建用户文档
     * @param rebuildUserDocumentReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/user/document/rebuild")
    Response<?> rebuildUserDocument(@RequestBody @Validated RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);
}
