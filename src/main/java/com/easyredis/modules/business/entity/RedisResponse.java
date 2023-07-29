package com.easyredis.modules.business.entity;

import lombok.Data;

/**
 * @author Asynchronous
 */
@Data
public class RedisResponse {
    private String key;
    private String type;
    private String value;
}
