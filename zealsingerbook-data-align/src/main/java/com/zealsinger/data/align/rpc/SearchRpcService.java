package com.zealsinger.data.align.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.search.api.SearchFeignApi;
import com.zealsinger.search.dto.RebuildNoteDocumentReqDTO;
import com.zealsinger.search.dto.RebuildUserDocumentReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class SearchRpcService {
    @Resource
    private SearchFeignApi searchFeignApi;

    /**
     * 笔记ES数据文档重构
     */
    public void rebuildNoteDocument(Long noteId){
        RebuildNoteDocumentReqDTO reqDTO = RebuildNoteDocumentReqDTO.builder().id(noteId).build();
        searchFeignApi.rebuildNoteDocument(reqDTO);
    }


    /**
     * 用户ES数据文档重构
     */
    public void rebuildUserDocument(Long userId){
        RebuildUserDocumentReqDTO reqDTO = RebuildUserDocumentReqDTO.builder().id(userId).build();
        searchFeignApi.rebuildUserDocument(reqDTO);
    }
}
