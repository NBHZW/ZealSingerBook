package com.zealsinger.kv.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateNoteContentReqDTO {
    @NotNull(message = "笔记uuid不能为空")
    private String contentUuid;
    private String content;
}
