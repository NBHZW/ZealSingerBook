package com.zealsinger.comment.server.impl;

import com.google.common.base.Preconditions;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.comment.constant.MQConstants;
import com.zealsinger.comment.domain.dto.PublishCommentMqDTO;
import com.zealsinger.comment.domain.vo.PublishCommentReqVO;
import com.zealsinger.comment.retry.SendMqRetryHelper;
import com.zealsinger.comment.rpc.IdGeneratorRpcService;
import com.zealsinger.comment.server.CommentService;
import com.zealsinger.frame.filter.LoginUserContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private SendMqRetryHelper sendMqRetryHelper;

    @Resource
    private IdGeneratorRpcService idGeneratorRpcService;

    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        Long noteId = publishCommentReqVO.getNoteId();
        String content = publishCommentReqVO.getContent();
        String imageUrl = publishCommentReqVO.getImageUrl();
        /**
         Preconditions.checkArgument(boolean 条件,String errorMessage)
         如果前面条件为false，则抛出异常并且携带errorMessage作为异常信息
         guava中的工具
         */
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),"内容和图片不能均为空");
        Long creatorId = LoginUserContextHolder.getUserId();
        PublishCommentMqDTO mqDTO = PublishCommentMqDTO.builder()
                .noteId(noteId)
                .content(content)
                .imageUrl(imageUrl)
                .createTime(LocalDateTime.now())
                .creatorId(creatorId)
                .replyCommentId(publishCommentReqVO.getReplyCommentId())
                .commentId(Long.valueOf(idGeneratorRpcService.generateCommentId()))
                .build();

        sendMqRetryHelper.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT, JsonUtil.ObjToJsonString(mqDTO));
        // 错误方案--sendMqRetryHelper.asySend(MQConstants.TOPIC_PUBLISH_COMMENT, JsonUtil.ObjToJsonString(mqDTO));
        return Response.success();
    }
}
