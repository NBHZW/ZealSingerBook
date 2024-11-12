package com.zealsinger.note.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_note_collection")
public class NoteCollection {
    @TableId(type = IdType.AUTO , value = "id")
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("note_id")
    private Long noteId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /**
     *  是否收藏 0取消收藏 1收藏
     */
    @TableField("status")
    private Integer status;

}