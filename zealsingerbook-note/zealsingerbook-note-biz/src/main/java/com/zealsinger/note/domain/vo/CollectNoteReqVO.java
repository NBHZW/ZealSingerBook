package com.zealsinger.note.domain.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CollectNoteReqVO {
    @NotNull(message = "笔记ID不能为空")
    private Long noteId;
}
