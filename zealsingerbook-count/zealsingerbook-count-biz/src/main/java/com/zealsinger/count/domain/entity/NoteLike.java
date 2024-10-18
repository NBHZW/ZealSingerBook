package com.zealsinger.count.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_note_like")
public class NoteLike {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;

    @TableField("note_id")
    private Long noteId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField("status")
    private Byte status;
}