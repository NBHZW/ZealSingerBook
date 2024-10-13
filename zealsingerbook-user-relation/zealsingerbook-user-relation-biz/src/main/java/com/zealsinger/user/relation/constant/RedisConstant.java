package com.zealsinger.user.relation.constant;

public class RedisConstant {
    public static final String FOLLOWING_KEY_PREFIX = "following:";
    public static final String FANS_KEY_PREFIX = "fans:";


    public static String getFollowingKey(String userId) {
        return FOLLOWING_KEY_PREFIX + userId;
    }

    public static String getFansKey(String userId) {
        return FANS_KEY_PREFIX + userId;
    }
}
