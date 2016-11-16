package com.littlersmall.redisaccess.cache;

import com.littlersmall.redisaccess.DataAccess;
import com.littlersmall.redisaccess.RedisAccessBuilder;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Created by littlersmall on 16/5/9.
 */
//用redis实现的有时效缓存封装
public class RedisCache {
    private DataAccess<String> dataAccess;

    public RedisCache(RedisTemplate<String, String> redisTemplate) {
        RedisAccessBuilder<String> redisAccessBuilder = new RedisAccessBuilder<>(redisTemplate);

        dataAccess = redisAccessBuilder.buildDataAccess(String.class, null);
    }

    //如果不存在,则插入并返回true
    //否则返回false
    public boolean cacheIfAbsent(String key, int validSecond) {
        String cacheKey = "cached#" + key;
        String cacheValue = "" + System.currentTimeMillis();

        return dataAccess.setNX(cacheKey, cacheValue, validSecond);
    }

    public boolean hasKey(String key) {
        String cacheKey = "cached#" + key;

        return null != dataAccess.get(cacheKey);
    }

    public void delete(String key) {
        String cacheKey = "cached#" + key;

        try {
            dataAccess.delete(cacheKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
