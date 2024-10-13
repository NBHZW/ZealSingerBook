package com.zealsinger.user.relation.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MqFollowUserDTO {
    private Long userId;

    private Long followUserId;

    private LocalDateTime createTime;
}
