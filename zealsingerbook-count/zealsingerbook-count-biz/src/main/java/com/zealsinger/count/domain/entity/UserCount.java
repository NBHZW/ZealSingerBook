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
@TableName("t_user_count")
public class UserCount {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("fans_total")
    private Long fansTotal;

    @TableField("following_total")
    private Long followingTotal;

    @TableField("note_total")
    private Long noteTotal;

    @TableField("like_total")
    private Long likeTotal;

    @TableField("collect_total")
    private Long collectTotal;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}