package com.zealsinger.user.relation.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FindFollowingListReqVO {
    /**
     * 查询用户的ID
     */
    private Long userId;
    /**
     * 当前页码
     */
    private Long pageNo;
}
