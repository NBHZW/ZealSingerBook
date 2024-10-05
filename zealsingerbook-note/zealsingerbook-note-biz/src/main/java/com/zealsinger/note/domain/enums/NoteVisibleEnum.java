package com.zealsinger.note.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NoteVisibleEnum {
    PUBLIC(0, "公开"),
    PRIVATE(1, "私有");
    private final Integer value;
    private final String desc;
}
