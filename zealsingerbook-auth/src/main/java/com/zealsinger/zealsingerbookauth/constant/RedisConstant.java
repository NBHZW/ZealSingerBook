package com.zealsinger.zealsingerbookauth.constant;

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
