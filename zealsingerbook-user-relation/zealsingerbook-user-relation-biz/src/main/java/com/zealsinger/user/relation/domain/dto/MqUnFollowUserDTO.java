package com.zealsinger.user.relation.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MqUnFollowUserDTO {
    private String userId;
    private String unfollowUserId;
    private LocalDateTime unfollowTime;
}
