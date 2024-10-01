package com.zealsinger.user.domain.vo;


import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserInfoReqVO {
    /**
     * zealsingerBook Id 账号唯一标识
     */
    private String zealsingerBookId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 性别 0:女 1:男
     */
    private Integer sex;

    /**
     * 头像
     */
    private MultipartFile avatar;

    /**
     * 背景图
     */
    private MultipartFile background;

    /**
     * 生日
     */
    private LocalDateTime birthday;

    /**
     * 个人介绍
     */
    private String introduction;

}
