package com.zealsinger.zealsingerbookauth.domain.enums;

import com.zealsinger.book.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {
    /**
     * code 采用 模块Name+序列的方式  方便出问题的时候定位到是哪个模块
     */
    SYSTEM_ERROR("AUTH-10000", "出错啦，请联系后台小哥，小哥要加班咯~"),
    PARAM_NOT_VALID("AUTH-10001", "参数错误"),
    REQUEST_FREQUENT("AUTH-20000","请求太频繁,3分钟后再尝试");
    private final String errorCode;
    private final String errorMessage;

}
