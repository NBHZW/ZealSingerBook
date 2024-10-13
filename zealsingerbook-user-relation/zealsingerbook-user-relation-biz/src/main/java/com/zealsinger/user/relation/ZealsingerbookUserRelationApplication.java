package com.zealsinger.user.relation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients("com.zealsinger")
public class ZealsingerbookUserRelationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZealsingerbookUserRelationApplication.class, args);
    }
}
