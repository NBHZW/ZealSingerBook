package com.zealsinger.frame.config;

import com.zealsinger.frame.filter.HeaderUserId2ContextFilter;
import com.zealsinger.frame.interceptor.FeignRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ContextAutoConfiguration {
    @Bean
    public FilterRegistrationBean<HeaderUserId2ContextFilter> filterFilterRegistrationBean() {
        HeaderUserId2ContextFilter filter = new HeaderUserId2ContextFilter();
        FilterRegistrationBean<HeaderUserId2ContextFilter> bean = new FilterRegistrationBean<>(filter);
        return bean;
    }

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }

}
