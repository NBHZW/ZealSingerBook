package com.zealsinger.user.domain.entity;

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
@TableName("t_permission")
public class Permission {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;

    @TableField("parent_id")
    private Long parentId;

    @TableField("name")
    private String name;

    @TableField("type")
    private Byte type;

    @TableField("menu_url")
    private String menuUrl;

    @TableField("menu_icon")
    private String menuIcon;

    @TableField("sort")
    private Integer sort;

    @TableField("permission_key")
    private String permissionKey;

    @TableField("status")
    private Byte status;


    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField("is_deleted")
    private Boolean isDeleted;
}