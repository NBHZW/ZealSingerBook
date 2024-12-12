package com.zealsinger.search.domain.enums;

import com.zealsinger.book.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {
    SYSTEM_ERROR("SEARCH_10000","系统异常"),
    PARAM_NOT_VALID("SEARCH-10001", "参数错误"),
    ;
    private final String errorCode;
    private final String errorMessage;

}
