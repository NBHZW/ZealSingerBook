package com.zealsinger.user.domain.enums;

import com.zealsinger.book.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {
    SYSTEM_ERROR("User-10000","出错啦，请联系后台小哥，小哥要加班咯~"),
    PARSE_ERROR("User-10001","参数解析失败"),
    USER_NOT_EXIST("User-10002","用户不存在"),
    NICK_NAME_VALID_FAIL("USER-20001", "昵称请设置2-24个字符，不能使用@《/等特殊字符"),
    ZEALSINGERBOOK_ID_VALID_FAIL("USER-20002", "zealsingerbookId请设置6-15个字符，仅可使用英文（必须）、数字、下划线"),
    SEX_VALID_FAIL("USER-20003", "性别错误"),
    INTRODUCTION_VALID_FAIL("USER-20004", "个人简介请设置1-100个字符"),
    UPLOAD_AVATAR_FAIL("USER-20005", "头像上传失败"),
    UPLOAD_BACKGROUND_IMG_FAIL("USER-20006", "背景图上传失败"),
    USER_NOT_FOUND("USER-20007", "该用户不存在"),
    PASSWORD_UPDATE_FAIL("USER-20008", "密码修改失败"),



    ;
    private String errorCode;
    private String errorMessage;

}
