package com.zealsinger.zealsingerbookauth;

import com.zealsinger.aspect.ZealLog;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zealsinger.zealsingerbookauth.mapper")
public class ZealsingerbookAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZealsingerbookAuthApplication.class, args);
    }

}
