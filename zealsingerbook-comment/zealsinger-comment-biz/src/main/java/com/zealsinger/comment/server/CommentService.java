package com.zealsinger.comment.server;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.comment.domain.vo.PublishCommentReqVO;

public interface CommentService {
    /**
     * 发布评论
     * @param publishCommentReqVO
     * @return
     */
    Response<?> publishComment(PublishCommentReqVO publishCommentReqVO);
}
