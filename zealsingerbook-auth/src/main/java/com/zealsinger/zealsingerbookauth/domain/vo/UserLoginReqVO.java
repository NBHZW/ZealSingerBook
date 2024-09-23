package com.zealsinger.zealsingerbookauth.domain.vo;

import com.zealsinger.book.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求体VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginReqVO {
    @NotNull(message = "手机号不能为空")
    @PhoneNumber
    private String phoneNumber;

    /**
     * 验证码
     */
    private String code;

    /**
     * 密码
     */
    private String password;

    /**
     * 登录类型：1手机号验证码，或者是2账号密码
     */
    @NotNull(message = "登录类型不能为空")
    private Integer type;
}
