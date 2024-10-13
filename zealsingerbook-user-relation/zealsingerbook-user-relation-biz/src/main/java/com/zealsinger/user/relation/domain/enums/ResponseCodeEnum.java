package com.zealsinger.user.relation.domain.enums;

import com.zealsinger.book.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {
    SYSTEM_ERROR("User-Realation-10000","出错啦，请联系后台小哥，小哥要加班咯~"),
    PARSE_ERROR("User-Realation-10001","参数解析失败"),
    CANT_FOLLOW_YOUR_SELF("User-Realation-20000","不能自己关注自己"),
    USER_NOT_EXITS("User-Realation-20001","用户不存在"),
    FOLLOWING_COUNT_LIMIT("RELATION-20002", "您关注的用户已达上限，请先取关部分用户"),
    ALREADY_FOLLOWED("RELATION-20003", "您已经关注了该用户"),
    CANT_UNFOLLOW_YOUR_SELF("RELATION-2004","不能自己取关自己"),
    NOT_FOLLOWED("RELATION-2005","未关注对方，无法取关")


    ;
    private String errorCode;
    private String errorMessage;

}
