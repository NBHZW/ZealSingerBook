package com.zealsinger.user.relation.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CountFollowUnfollowMqDTO {
    private Long userId;
    private Long targetUserId;
    /**
     * 0取消关注  1关注
     */
    private Integer type;
}
