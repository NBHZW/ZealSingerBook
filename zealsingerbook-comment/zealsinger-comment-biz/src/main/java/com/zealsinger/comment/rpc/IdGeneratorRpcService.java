package com.zealsinger.comment.rpc;

import com.zealsinger.id.generator.api.IdGeneratorApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IdGeneratorRpcService {
    @Resource
    private IdGeneratorApi idGeneratorApi;

    /**
     * 生成评论 ID
     *
     * @return
     */
    public String generateCommentId() {
        return idGeneratorApi.getSegmentId("leaf-segment-comment-id");
    }
}
