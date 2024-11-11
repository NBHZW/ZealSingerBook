package com.zealsinger.note.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@AllArgsConstructor
@Getter
public enum NoteBloomLuaResultEnmu {
    // 布隆过滤器不存在
    BLOOM_NOT_EXIST(-1L),
    // 笔记已点赞
    NOTE_LIKED(1L),

    // ZSET不存在
    ZSET_NOT_EXIST(-1L),

    // Lua结果成功
    SUCCESS(0L),

    // 笔记未点赞
    NOTE_UNLIKED(1L)
    ;
    private final Long code;

    /**
     * 根据类型 code 获取对应的枚举
     * @param code
     * @return
     */
    public static NoteBloomLuaResultEnmu valueOf(Long code) {
        for (NoteBloomLuaResultEnmu noteLikeLuaResultEnum : NoteBloomLuaResultEnmu.values()) {
            if (Objects.equals(code, noteLikeLuaResultEnum.getCode())) {
                return noteLikeLuaResultEnum;
            }
        }
        return null;
    }
}
