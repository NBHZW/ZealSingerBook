package com.zealsinger.count.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FollowUnfollowTypeEnum {
    FOLLOW(1),
    UNFOLLOW(0);
    ;
    private Integer value;

    public static FollowUnfollowTypeEnum getFollowUnfollowTypeEnum(Integer value) {
        FollowUnfollowTypeEnum[] followUnfollowTypeEnums = FollowUnfollowTypeEnum.values();
        for (FollowUnfollowTypeEnum followUnfollowTypeEnum : followUnfollowTypeEnums) {
            if (followUnfollowTypeEnum.getValue().equals(value)) {
                return followUnfollowTypeEnum;
            }
        }
        return null;
    }
}
