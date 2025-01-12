package com.zealsinger.comment.controller;

import com.zealsinger.aspect.ZealLog;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.comment.domain.vo.PublishCommentReqVO;
import com.zealsinger.comment.server.CommentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.ConditionalOnEnabledResourceChain;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/comment")
public class CommentController {
    @Resource
    private CommentService commentService;

    @PostMapping("/publish")
    @ZealLog(description = "发布评论")
    public Response<?> publishCommetn(@RequestBody @Validated PublishCommentReqVO publishCommentReqVO){
        return commentService.publishComment(publishCommentReqVO);
    }
}
