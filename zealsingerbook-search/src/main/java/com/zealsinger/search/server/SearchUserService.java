package com.zealsinger.search.server;

import com.zealsinger.book.framework.common.response.PageResponse;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.search.domain.vo.SearchNoteReqVO;
import com.zealsinger.search.domain.vo.SearchNoteRspVO;
import com.zealsinger.search.domain.vo.SearchUserReqVO;
import com.zealsinger.search.domain.vo.SearchUserRspVO;

public interface SearchUserService {
    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);

    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);
}
