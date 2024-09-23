package com.zealsinger.book.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StatusEnum {
    // 启用
    ENABLE((byte) 0),
    // 禁用
    DISABLED((byte) 1);

    private final Byte value;
}
