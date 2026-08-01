package com.smartlife.utils;

import lombok.Data;

@Data
public class RedisData {
    private long expireTime;
    private Object data;
}
