package com.zealsinger.note.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateVisibleOnlyMeReqVO {
    @NotNull(message = "笔记ID不能为空")
    private String id;

    @NotNull(message = "操作类型不能为空")
    private Boolean visibleOnlyMe;
}
