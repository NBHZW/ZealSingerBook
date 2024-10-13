package com.zealsinger.user.relation.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
@Builder
public class FollowReqDTO {
    @NotNull(message = "关注用户ID不能为空")
    private Long followUserId;
}
