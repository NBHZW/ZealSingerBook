package com.zealsinger.zealsingerbookauth;

import com.alibaba.druid.filter.config.ConfigTools;
import com.zealsinger.zealsingerbookauth.domain.entity.UserDO;
import com.zealsinger.zealsingerbookauth.mapper.UserDOMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ZealsingerbookAuthApplicationTests {
    @Resource
    private UserDOMapper userTestMapper;

    @Test
    void connectTest(){
        UserDO userDO = userTestMapper.selectByPrimaryKey(3L);
        System.out.println(userDO);
    }

    @Test
    void contextLoads() throws Exception {
        String password = "yunfuwuxiaoguanjia";
        String[] arr = ConfigTools.genKeyPair(512);

        // 私钥
        System.out.println("privateKey: {" + arr[0] + "}");
        // 公钥
        System.out.println("publicKey: {" + arr[1] + "}");

        // 通过私钥加密密码
        String encodePassword = ConfigTools.encrypt(arr[0], password);
        System.out.println(("password: " + encodePassword));
    }

}
