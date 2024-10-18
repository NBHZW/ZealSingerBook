package com.zealsinger.count.config;

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
        // 任务队列长度
        executor.setQueueCapacity(200);
        // 线程活跃时间
        executor.setKeepAliveSeconds(30);
        // 线程名前缀
        executor.setThreadNamePrefix("User-Relation-Executor-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 设置最大运行时间
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
