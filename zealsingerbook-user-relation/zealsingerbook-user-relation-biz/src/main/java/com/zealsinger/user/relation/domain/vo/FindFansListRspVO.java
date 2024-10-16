package com.zealsinger.user.relation.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FindFansListRspVO {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 笔记总数
     */
    private Long noteCount;

    /**
     * 粉丝量
     */
    private Long fansCount;
}
