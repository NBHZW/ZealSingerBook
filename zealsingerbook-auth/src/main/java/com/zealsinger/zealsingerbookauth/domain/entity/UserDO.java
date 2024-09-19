package com.zealsinger.zealsingerbookauth.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDO {
    private Long id;

    private String username;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}