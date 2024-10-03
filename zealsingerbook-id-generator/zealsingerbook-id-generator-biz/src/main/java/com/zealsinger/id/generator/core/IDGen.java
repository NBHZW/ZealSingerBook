package com.zealsinger.id.generator.core;


import com.zealsinger.id.generator.core.common.Result;

public interface IDGen {
    Result get(String key);
    boolean init();
}
