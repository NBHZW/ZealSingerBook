package com.zealsinger.note.domain.enums;

import com.zealsinger.book.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {
    SYSTEM_ERROR("NOTE_10000","系统异常"),
    PARAM_NOT_VALID("NOTE-10001", "参数错误"),
    TYPE_ERROR("NOTE_20000","笔记类型不存在"),
    NOTE_PUBLISH_FAIL("NOTE-20001", "笔记发布失败"),
    NOTE_NOT_FOUND("NOTE-20002", "笔记不存在"),
    NOTE_PRIVATE("NOTE-20003", "作者已将该笔记设置为仅自己可见"),
    NOTE_UPDATE_FAIL("NOTE-20004", "笔记更新失败"),
    ;
    private final String errorCode;
    private final String errorMessage;

}
