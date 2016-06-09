package com.littlersmall.redisaccess.example;

import com.littlersmall.redisaccess.DataProcess;
import com.littlersmall.redisaccess.DataProcessWithLock;
import com.littlersmall.redisaccess.RedisDataProcessWithLockBuilder;
import com.littlersmall.redisaccess.common.DetailRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Created by littlersmall on 16/6/9.
 */
@Service
public class RedisExample {
    @Autowired
    RedisTemplate<String, String> stringRedisTemplate;

    @Autowired
    RedisTemplate<String, User> userRedisTemplate;

    private DataProcessWithLock<User> dataProcessWithLock;

    @PostConstruct
    public void init() {
        RedisDataProcessWithLockBuilder redisDataProcessWithLockBuilder =
                new RedisDataProcessWithLockBuilder(stringRedisTemplate);

        dataProcessWithLock = redisDataProcessWithLockBuilder.buildDataProcessWithLock(new DataProcess<User>() {
            //用户自己的实现
            @Override
            public DetailRes process(String key, User user) {
                System.out.println(user);

                return new DetailRes(true, "");
            }
        });
    }

    public void process() {
        User user = new User(111, "littlersmall");
        String key = "" + user.getUserId();

        dataProcessWithLock.execute(key, user);
    }
}
