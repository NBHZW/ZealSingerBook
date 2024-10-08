package com.zealsinger.note.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateTopStatusDTO {
    @NotNull(message = "笔记ID不能为空")
    private String id;
    @NotNull(message = "置顶状态不能为空")
    private Boolean isTop;
}
