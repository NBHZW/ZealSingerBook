package com.zealsinger.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@EnableFeignClients("com.zealsinger")
public class ZealsingerbookCommentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZealsingerbookCommentApplication.class, args);
    }
}
