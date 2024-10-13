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
    SET_ONLY_ME_FAIL("NOTE-20005", "设置仅自己可见失败"),
    SET_NOTE_TOP_FAIL("NOTE-20006","笔记置顶失败"),
    NOT_HAVE_PERMISSIONS("NOTE-20007","不是笔记发布者，无权操作"),
    ;
    private final String errorCode;
    private final String errorMessage;

}
