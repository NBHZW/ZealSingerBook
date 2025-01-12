package com.zealsinger.comment.rpc;

import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.kv.api.KVFeignApi;
import com.zealsinger.kv.dto.BatchAddCommentContentReqDTO;
import com.zealsinger.kv.dto.CommentContentReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class KvRpcService {
    @Resource
    private KVFeignApi kvFeignApi;

    public boolean batchSaveCommentContent(List<CommentContentReqDTO> commentContentReqDTOList){
        BatchAddCommentContentReqDTO batchAddCommentContentReqDTO = new BatchAddCommentContentReqDTO();
        batchAddCommentContentReqDTO.setComments(commentContentReqDTOList);
        Response<?> response = kvFeignApi.batchAddCommentContent(batchAddCommentContentReqDTO);
        if (!response.isSuccess()) {
            throw new RuntimeException("批量保存评论内容失败");
        }
        return true;
    }
}
