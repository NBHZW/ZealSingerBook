package com.zealsinger.zealsingerbookauth.filter;


import com.alibaba.ttl.TransmittableThreadLocal;
import com.zealsinger.book.framework.common.constant.GlobalConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LoginUserContextHolder {
    // 初始化一个 ThreadLocal 变量 withInitial方法接收一个Supplier函数接口 可以让每个线程第一次调用get方法时延迟初始化变量 也就是惰性加载 要使用的时候才会ThreadLocal中创建HashMap
    private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
            = TransmittableThreadLocal.withInitial(HashMap::new);

    public static void set(String key, Object value) {
        Map<String, Object> map = LOGIN_USER_CONTEXT_THREAD_LOCAL.get();
        map.put(key, value);
    }

    public static Object get(String key) {
        Map<String, Object> map = LOGIN_USER_CONTEXT_THREAD_LOCAL.get();
        return Objects.isNull(map) ? null : map.get(key);
    }


    /**
     * 设置用户 ID
     *
     * @param value
     */
    public static void setUserId(Object value) {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.get().put(GlobalConstants.USER_ID, value);
    }

    /**
     * 获取用户 ID
     *
     * @return
     */
    public static Long getUserId() {
        Object value = LOGIN_USER_CONTEXT_THREAD_LOCAL.get().get(GlobalConstants.USER_ID);
        if (Objects.isNull(value)) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    /**
     * 删除 ThreadLocal
     */
    public static void remove() {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }
}
