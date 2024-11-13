package com.zealsinger.note.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NoteCollectionStatusEnum {

    COLLECTION(1),
    UNCOLLECTION(0)
    ;
    private final Integer code;

    public static NoteCollectionStatusEnum getByCode(Integer code) {
        for (NoteCollectionStatusEnum noteCollectionStatusEnum : NoteCollectionStatusEnum.values()) {
            if (noteCollectionStatusEnum.getCode().equals(code)) {
                return noteCollectionStatusEnum;
            }
        }
        return null;
    }
}
