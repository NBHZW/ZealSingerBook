package com.zealsinger.note.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FindNoteByIdReqDTO {
    @NotNull(message = "笔记ID不能为空")
    private String noteId;
}
