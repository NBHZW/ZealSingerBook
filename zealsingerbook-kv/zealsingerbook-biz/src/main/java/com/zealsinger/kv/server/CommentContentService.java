package com.zealsinger.kv.server;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.dto.BatchAddCommentContentReqDTO;

public interface CommentContentService {
    Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);
}
