package com.zealsinger.note.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(50);
        // 任务队列容量
        executor.setQueueCapacity(200);
        // 线程活跃时间（秒）
        executor.setKeepAliveSeconds(30);
        // 线程池前缀
        executor.setThreadNamePrefix("zealsinger-note-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭 所有任务结束之后再关闭线程
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 强制关闭  60s还没完成强制关闭
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
