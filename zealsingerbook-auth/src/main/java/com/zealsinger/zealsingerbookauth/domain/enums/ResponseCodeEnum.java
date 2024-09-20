package com.zealsinger.zealsingerbookauth.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Getter
public enum ResponseCodeEnum {
    /**
     * code 采用 模块Name+序列的方式  方便出问题的时候定位到是哪个模块
     */
    SYSTEM_ERROR("AUTH-10000", "出错啦，请联系后台小哥，小哥要加班咯~"),
    PARAM_NOT_VALID("AUTH-10001", "参数错误");
    private final String code;
    private final String message;

}
