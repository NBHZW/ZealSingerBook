package com.zealsinger.book.framework.common.constant;

public class RedisConstant {
    public static final String VERIFICATION_CODE_KEY_PREFIX="zealsinger_verification_code:";
    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";


    /**
     * 用户信息缓存 KEY 前缀
     */
    public static final String USER_INFO_KEY = "user:info:";

    /**
     * 笔记详情缓存Key前缀
     */
    public static final String NOTE_DETAIL_KEY_PREFIX = "note:detail:";

    /**
     * 布隆过滤器笔记点赞Redis前缀
     */
    public static final String BLOOM_USER_NOTE_LIKE_LIST_KEY = "bloom:note:likes:";

    /**
     * 用户笔记点赞列表 ZSet 前缀
     */
    public static final String USER_NOTE_LIKE_ZSET_KEY = "user:note:likes:";

    /**
     *  布隆过滤器笔记收藏Redis前缀
     */
    public static final String BLOOM_USER_NOTE_COLLECT_LIST_KEY = "bloom:note:collects:";

    /**
     * 用户笔记收藏列表ZSet前缀
     */
    public static final String USER_NOTE_COLLECTION_ZSET_KEY = "user:note:collects:";

    /**
     * 构建用户笔记收藏列表ZSET的RedisKey
     */
    public static String buildUserCollectionZSetKey(Long userId){
        return USER_NOTE_COLLECTION_ZSET_KEY + userId;
    }

    /**
     *  构建完整的布隆过滤器笔记收藏Redis
     */
    public static String buildBloomUserNoteCollectsKey(Long userId){
        return BLOOM_USER_NOTE_COLLECT_LIST_KEY+userId;
    }
    /**
     * 构建完整的用户笔记点赞列表 ZSet KEY
     * @param userId
     * @return
     */
    public static String buildUserNoteLikeZSetKey(Long userId) {
        return USER_NOTE_LIKE_ZSET_KEY + userId;
    }

    public static String getBloomUserNoteLikeListKey(Long userId) {
        return BLOOM_USER_NOTE_LIKE_LIST_KEY + userId;
    }

    /**
     * 获取笔记详情缓存id
     */
    public static String getNoteCacheId(String noteId){
        return NOTE_DETAIL_KEY_PREFIX + noteId;
    }

    /**
     * 用户信息缓存id
     * @param userId
     * @return
     */
    public static String getUserInfoKey(Long userId) {
        return USER_INFO_KEY + userId;
    }

    /**
     * 构建角色对应的权限集合 KEY
     * @param
     * @return
     */
    public static String buildRolePermissionsKey(String roleKey) {
        return ROLE_PERMISSIONS_KEY_PREFIX + roleKey;
    }


    /**
     * 构建验证码 KEY
     * @param loginId
     * @return
     */
    public static String buildUserRoleKey(Long loginId) {
        return USER_ROLES_KEY_PREFIX + loginId;
    }

    public static String getVerificationCodeKeyPrefix(String phone){
        return VERIFICATION_CODE_KEY_PREFIX+phone;
    }
}
