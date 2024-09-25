package com.zealsinger.zealsingerbookauth;

import com.alibaba.druid.filter.config.ConfigTools;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class ZealsingerbookAuthApplicationTests {
    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;


    @Test
    void contextLoads() throws Exception {
        String password = "hzw123";
        String[] arr = ConfigTools.genKeyPair(512);

        // 私钥
        System.out.println("privateKey: {" + arr[0] + "}");
        // 公钥
        System.out.println("publicKey: {" + arr[1] + "}");

        // 通过私钥加密密码
        String encodePassword = ConfigTools.encrypt(arr[0], password);
        System.out.println(("password: " + encodePassword));
    }

    @Test
    void redisTest(){
        redisTemplate.opsForValue().set("zealsingerbook:auth:user0","zealsingerTest123");
        redisTemplate.opsForValue().set("zealsingerbook:auth:user1","zealsingerTest234",30, TimeUnit.SECONDS);
        System.out.println(Objects.requireNonNull(redisTemplate.opsForValue().get("zealsingerbook:auth:user0")));
    }

    @Test
    void threadPoolTest(){
        taskExecutor.submit(()-> System.out.println("异步输出：异步测试"+Thread.currentThread().getName()));
    }

}
