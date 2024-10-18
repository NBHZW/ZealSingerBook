package com.zealsinger.count.domain.enums;

import com.zealsinger.book.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {
    SYSTEM_ERROR("Count-10000","出错啦，请联系后台小哥，小哥要加班咯~"),

    ;
    private String errorCode;
    private String errorMessage;

}
