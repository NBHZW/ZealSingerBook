package com.zealsinger.book.framework.common.constant;

public class RedisConstant {
    public static final String VERIFICATION_CODE_KEY_PREFIX="zealsinger_verification_code:";


    /**
     * 用户账户ID
     */
    public static final String ZEALSINGER_BOOK_ID_GENERATOR_KEY="zealsinger_id_generator";

    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";


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
     * @param phone
     * @return
     */
    public static String buildUserRoleKey(String phone) {
        return USER_ROLES_KEY_PREFIX + phone;
    }

    public static String getVerificationCodeKeyPrefix(String phone){
        return VERIFICATION_CODE_KEY_PREFIX+phone;
    }
}
