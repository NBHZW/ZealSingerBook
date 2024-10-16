package com.zealsinger.zealsingerbookauth.server.Impl;

import cn.hutool.core.util.RandomUtil;
import com.zealsinger.book.framework.common.exception.BusinessException;
import com.zealsinger.book.framework.common.response.Response;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.zealsingerbookauth.domain.enums.ResponseCodeEnum;
import com.zealsinger.zealsingerbookauth.domain.vo.SendVerificationCodeReqVO;
import com.zealsinger.zealsingerbookauth.server.VerificationCodeService;
import com.zealsinger.zealsingerbookauth.sms.AliyunHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;


import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private AliyunHelper aliyunHelper;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        String phone = sendVerificationCodeReqVO.getPhone();
        String redisKey = RedisConstant.getVerificationCodeKeyPrefix(phone);
        Boolean hasKey = redisTemplate.hasKey(redisKey);
        if(Boolean.TRUE.equals(hasKey)){
            // 存在 提是不要频繁请求
            throw new BusinessException(ResponseCodeEnum.REQUEST_FREQUENT);
        }
        // 不存在 生成验证码并且发送过去
        String code = RandomUtil.randomNumbers(6).replace("\"","");
        Integer intCode = Integer.valueOf(code);
        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, code);

        // todo 异步调用第三方服务发送消息
        taskExecutor.submit(()->{
            String signName = "singer预约";
            String templateCode = "SMS_473835044";
            String resultCode = String.format("{\"code\":\"%s\"}", code);
            aliyunHelper.sendMessage(signName,templateCode,phone,resultCode);
        });

        log.info("成功向手机号 {}  发送验证码 {} , 有效期 3min",phone,code);
        // 存入redis并且设置过期时间
        // redisTemplate.opsForValue().set(redisKey,intCode,3, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(redisKey,intCode);
        return Response.success();
    }
}
