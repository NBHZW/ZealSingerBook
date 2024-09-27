package com.zealsinger.zealsingerbookauth.domain.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePasswordReqVO {
    @NotBlank(message = "新密码不能为空")
    private String password;
}
