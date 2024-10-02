package com.zealsinger.oss.enums;

import com.zealsinger.book.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {
    /**
     *  错误码和错误信息枚举类
     */
    SYSTEM_ERROR("Oss-10000","出错啦，请联系后台小哥，小哥要加班咯~"),
    ALIOSS_ERROR("Oss-10001","图片存储服务出错"),
    MINIO_ERROR("Oss-10002","图片存储服务出错"),
    FILE_BLANK_ERROR("Oss-10003","上传图片不能为空");

    private final String errorCode;
    private final String errorMessage;

}
