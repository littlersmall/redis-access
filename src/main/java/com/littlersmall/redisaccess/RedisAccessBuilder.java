package com.littlersmall.redisaccess;

import com.littlersmall.redisaccess.common.Constants;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by littlersmall on 16/5/7.
 */
public class RedisAccessBuilder<T> {
    private RedisTemplate<String, T> redisTemplate;

    public RedisAccessBuilder(RedisTemplate<String, T> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //1 设置json序列化方法
    //2 设置redis访问器
    //3 构造匿名类
    public DataAccess<T> buildDataAccess(Class<T> clazz, final DbDataGet<T> dbDataGet) {
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        //1
        if (clazz.equals(String.class)) {
            redisTemplate.setValueSerializer(new StringRedisSerializer());
        } else {
            redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<T>(clazz));
        }

        //2
        final ValueOperations<String, T> valueOperations= redisTemplate.opsForValue();
        final ListOperations<String, T> listOperations = redisTemplate.opsForList();

        //3
        return new DataAccess<T>() {
            @Override
            public void set(String key, T value) {
                valueOperations.set(key, value);
            }

            @Override
            public void set(String key, T value, int validTime) {
                set(key, value);
                redisTemplate.expire(key, validTime, TimeUnit.SECONDS);
            }

            @Override
            public T get(String key) {
                T data = valueOperations.get(key);

                //如果redis中没有且设置了数据库源,则从数据库中获取
                if (null == data
                        && null != dbDataGet) {
                    data = dbDataGet.get(key);

                    if (null != data) {
                        //默认保存一天
                        set(key, data, Constants.ONE_DAY);
                    }

                    try {
                        //避免数据库压力过大
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                return data;
            }

            @Override
            public List<T> popAll(String key) {
                String listKey = "list#" + key;
                List<T> resList = listOperations.range(listKey, 0, -1);

                delete(listKey);

                return resList;
            }

            @Override
            public void push(String key, T value) {
                String listKey = "list#" + key;
                listOperations.rightPush(listKey, value);
            }

            @Override
            public boolean setNX(String key, T value, int validTime) {
                if (valueOperations.setIfAbsent(key, value)) {
                    //todo throw exception
                    if (validTime > 0) {
                        return redisTemplate.expire(key, validTime, TimeUnit.SECONDS);
                    } else {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public void delete(String key) {
                redisTemplate.delete(key);
            }

            @Override
            public boolean tryLock(String key, T value, int timeout) {
                String lockKey = "locked#" + key;
                long startTime = System.currentTimeMillis();
                int sleepTimes = 0;

                while (!setNX(lockKey, value, Constants.MAX_LOCK_TIME)) {
                    try {
                        Thread.sleep(1);

                        sleepTimes++;

                        if (sleepTimes % 100 == 0) {
                            //超时判断
                            if (System.currentTimeMillis() - startTime > Constants.MAX_TIMEOUT * 1000) {
                                return false;
                            }
                        }
                    } catch (InterruptedException e) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public void unLock(String key) {
                String lockKey = "locked#" + key;

                delete(lockKey);
            }
        };
    }
}
