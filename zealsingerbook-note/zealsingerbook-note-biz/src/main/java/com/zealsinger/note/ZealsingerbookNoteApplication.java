package com.zealsinger.note;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients("com.zealsinger")
public class ZealsingerbookNoteApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZealsingerbookNoteApplication.class,args);
    }
}
