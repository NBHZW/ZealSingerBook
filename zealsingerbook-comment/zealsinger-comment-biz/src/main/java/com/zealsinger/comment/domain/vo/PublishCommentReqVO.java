package com.zealsinger.comment.domain.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishCommentReqVO {
    @NotNull(message = "笔记ID不能为空")
    private String noteId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 图片链接
     */
    private String imageUrl;

    /**
     * 回复的评论父ID
     */
    private Long replyCommentId;
}
