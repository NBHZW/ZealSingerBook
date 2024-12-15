package com.zealsinger.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZealsingerbookSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZealsingerbookSearchApplication.class,args);
    }
}
