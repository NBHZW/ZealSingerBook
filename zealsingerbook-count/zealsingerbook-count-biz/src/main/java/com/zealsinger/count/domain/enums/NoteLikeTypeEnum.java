package com.zealsinger.count.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NoteLikeTypeEnum {
    LIKE(1),
    UNLIKE(0);

    private final Integer code;

    public static NoteLikeTypeEnum getByCode(Integer code) {
        for (NoteLikeTypeEnum like : NoteLikeTypeEnum.values()) {
            if (like.getCode().equals(code)) {
                return like;
            }
        }
        return null;
    }
}
