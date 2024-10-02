package com.zealsinger.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.zealsinger")
public class ZealsingerBookUerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZealsingerBookUerApplication.class,args);
    }
}
