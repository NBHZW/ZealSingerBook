package com.zealsinger.kv.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddNoteContentReqDTO {
    @NotBlank(message = "笔记内容uuid不能为空")
    private String id;
    @NotBlank(message = "笔记内容不能为空")
    private String content;
}
