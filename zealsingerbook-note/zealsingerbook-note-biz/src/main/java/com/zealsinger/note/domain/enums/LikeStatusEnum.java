package com.zealsinger.note.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum LikeStatusEnum {
    LIKE(1),
    UNLIKE(0);
    ;
    private final Integer code;
}
