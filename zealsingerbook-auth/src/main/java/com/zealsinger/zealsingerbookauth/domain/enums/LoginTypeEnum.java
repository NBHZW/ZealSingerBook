package com.zealsinger.zealsingerbookauth.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

@Getter
@AllArgsConstructor
public enum LoginTypeEnum {

    VERIFICATION_CODE(1),
    PASSWORD(2);

    private final Integer value;
}
