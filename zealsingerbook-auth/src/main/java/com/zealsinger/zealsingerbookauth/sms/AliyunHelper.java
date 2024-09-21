package com.zealsinger.zealsingerbookauth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.zealsinger.book.framework.common.util.JsonUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AliyunHelper {
    @Resource
    private Client client;

    public boolean sendMessage(String signName, String templateCode,String phone,String templateParam){
        SendSmsRequest sendSmsRequest = new SendSmsRequest().setSignName(signName)
                .setTemplateCode(templateCode)
                .setPhoneNumbers(phone)
                .setTemplateParam(templateParam);

        RuntimeOptions runtime=new RuntimeOptions();
        try{
            log.info("==> 开始短信发送, phone: {}, signName: {}, templateCode: {}, templateParam: {}", phone, signName, templateCode, templateParam);
            SendSmsResponse response = client.sendSmsWithOptions(sendSmsRequest, runtime);
            log.info("==> 短信发送成功, response{}", JsonUtil.ObjToJsonString(response));
            return true;
        } catch (Exception e) {
            log.info("==> 短信发送失败:",e);
            return false;
        }

    }
}
