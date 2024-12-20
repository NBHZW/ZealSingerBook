package com.zealsinger.data.align;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.zealsinger.data.align")
public class ZealsingerbookDataAlignApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZealsingerbookDataAlignApplication.class,args);
    }
}
