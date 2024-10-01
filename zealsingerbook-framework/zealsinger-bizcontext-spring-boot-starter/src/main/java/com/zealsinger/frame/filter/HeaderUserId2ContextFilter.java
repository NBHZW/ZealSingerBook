package com.zealsinger.frame.filter;

import com.zealsinger.book.framework.common.constant.GlobalConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(GlobalConstants.USER_ID);
        log.info("## HeaderUserId2ContextFilter, 用户 ID: {}", userId);
        if(StringUtils.isNotBlank(userId)){
            LoginUserContextHolder.setUserId(userId);
            log.info("## 用户 ID: {} 存入ThreadLocal", userId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                // 一定要删除 ThreadLocal ，防止内存泄露
                LoginUserContextHolder.remove();
                log.info("===== 删除 ThreadLocal， userId: {}", userId);
            }
        }else{
            filterChain.doFilter(request, response);
        }
    }
}
