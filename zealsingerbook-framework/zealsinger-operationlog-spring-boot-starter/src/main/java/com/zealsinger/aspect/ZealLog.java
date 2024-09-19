package com.zealsinger.aspect;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface ZealLog {
    /**
     * API接口功能描述
     */
    String description() default "";
}
