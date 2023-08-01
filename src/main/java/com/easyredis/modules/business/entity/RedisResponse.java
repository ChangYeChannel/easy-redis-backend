package com.easyredis.modules.business.entity;

import lombok.Data;

/**
 * @author Asynchronous
 */
@Data
public class RedisResponse {
    private String datasource;
    private String key;
    private String type;
    private String value;
    private String isTTL;
    private Long ttl;

    public RedisResponse(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public RedisResponse() {

    }
}
