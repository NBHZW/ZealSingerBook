package com.zealsinger.note.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor
public enum NoteTypeEnum {
    /**
     * 图文枚举区分
     */
    TEXT(0, "图文"),
    VIDEO(1, "视频");
    private final Integer value;
    private final String desc;

    /**
     * 类型是否有效
     *
     * @param code
     * @return
     */
    public static boolean isValid(Integer code) {
        for (NoteTypeEnum noteTypeEnum : NoteTypeEnum.values()) {
            if (Objects.equals(code, noteTypeEnum.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     *通过code拿类型
     */
    public static NoteTypeEnum getType(Integer code){
        for (NoteTypeEnum enums : NoteTypeEnum.values()) {
            if(Objects.equals(code,enums.getValue())){
                return enums;
            }
        }
        return null;
    }
}
