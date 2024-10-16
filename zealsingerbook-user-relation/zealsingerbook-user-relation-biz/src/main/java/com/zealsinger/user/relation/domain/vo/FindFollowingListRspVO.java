package com.zealsinger.user.relation.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FindFollowingListRspVO {
    private Long userId;

    private String avatar;

    private String nickname;

    private String introduction;
}
