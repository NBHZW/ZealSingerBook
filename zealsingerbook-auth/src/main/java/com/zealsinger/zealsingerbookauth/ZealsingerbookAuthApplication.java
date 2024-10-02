package com.zealsinger.zealsingerbookauth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.zealsinger.user")
@MapperScan("com.zealsinger.zealsingerbookauth.mapper")
public class ZealsingerbookAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZealsingerbookAuthApplication.class, args);
    }

}
