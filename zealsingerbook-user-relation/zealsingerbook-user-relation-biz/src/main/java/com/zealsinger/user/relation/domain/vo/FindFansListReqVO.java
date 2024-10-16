package com.zealsinger.user.relation.domain.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FindFansListReqVO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    /**
     * 当前页  默认为第一页
     */
    @NotNull(message = "页码不能为空")
    private Long pageNo = 1L;
}
