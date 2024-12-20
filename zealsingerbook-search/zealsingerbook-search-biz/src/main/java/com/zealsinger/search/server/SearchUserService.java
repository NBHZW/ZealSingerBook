package com.zealsinger.search.server;

import com.zealsinger.book.framework.common.response.PageResponse;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.search.domain.vo.SearchNoteReqVO;
import com.zealsinger.search.domain.vo.SearchNoteRspVO;
import com.zealsinger.search.domain.vo.SearchUserReqVO;
import com.zealsinger.search.domain.vo.SearchUserRspVO;
import com.zealsinger.search.dto.RebuildNoteDocumentReqDTO;
import com.zealsinger.search.dto.RebuildUserDocumentReqDTO;

public interface SearchUserService {
    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);

    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);

    /**
     * 重建笔记文
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);

    /**
     * 重建用户文档
     * @param rebuildUserDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);

}
