package com.zealsinger.user.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 性别枚举
 */
@Getter
@AllArgsConstructor
public enum SexEnum {

    WOMAN(0),
    MAN(1),
    ;


    private  final Integer value;

    public static boolean isValid(Integer value) {
        for (SexEnum sexEnum : SexEnum.values()) {
            if(sexEnum.value.equals(value)){
                return true;
            }
        }
        return false;
    }
}
