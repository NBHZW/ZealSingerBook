package com.zealsinger.comment.domain.entry;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_comment")
public class Comment {
    @TableId("id")
    private Long id;

    @TableField("note_id")
    private Long noteId;

    @TableField("user_id")
    private Long userId;

    @TableField("content_uuid")
    private String contentUuid;

    @TableField("is_content_empty")
    private Boolean isContentEmpty;

    @TableField("image_url")
    private String imageUrl;

    @TableField("level")
    private Integer level;

    @TableField("reply_total")
    private Long replyTotal;

    @TableField("like_total")
    private Long likeTotal;

    @TableField("parent_id")
    private Long parentId;

    @TableField("reply_comment_id")
    private Long replyCommentId;

    @TableField("reply_user_id")
    private Long replyUserId;

    @TableField("is_top")
    private Boolean isTop;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}