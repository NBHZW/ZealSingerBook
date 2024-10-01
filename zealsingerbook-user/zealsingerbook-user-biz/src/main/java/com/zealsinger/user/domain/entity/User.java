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
@TableName("t_user")
public class User {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;

    @TableField("zealsinger_book_id")
    private String zealsingerBookId;

    @TableField("password")
    private String password;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar")
    private String avatar;

    @TableField("birthday")
    private LocalDateTime birthday;

    @TableField("background_img")
    private String backgroundImg;

    @TableField("phone")
    private String phone;

    @TableField("sex")
    private Integer sex;

    @TableField("status")
    private Integer status;

    @TableField("introduction")
    private String introduction;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField("is_deleted")
    private Boolean isDeleted;

}