package com.littlersmall.redisaccess.example;

import com.littlersmall.redisaccess.*;
import com.littlersmall.redisaccess.common.DetailRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * Created by littlersmall on 16/6/9.
 */
@Service
public class RedisExample {
    @Autowired
    RedisTemplate<String, String> stringRedisTemplate;

    @Autowired
    RedisTemplate<String, List> userRedisTemplate;

    private DataProcessWithLock<List> dataProcessWithLock;
    private DataAccess<List> dataAccess;

    @PostConstruct
    public void init() {
        dataAccess = new RedisAccessBuilder<>(userRedisTemplate).buildDataAccess(List.class, null);

        RedisDataProcessWithLockBuilder redisDataProcessWithLockBuilder =
                new RedisDataProcessWithLockBuilder(stringRedisTemplate);

        dataProcessWithLock = redisDataProcessWithLockBuilder.buildDataProcessWithLock(new DataProcess<List>() {
            //用户自己的实现
            @Override
            public DetailRes process(String key, List users) {
                System.out.println(users);

                return new DetailRes(true, "");
            }
        });
    }

    public DetailRes process() {
        User user = new User();
        String key = "" + user.getUserId();

        return dataProcessWithLock.execute(key, Arrays.asList(user));
    }

    public static void main(String[] args) {
        ApplicationContext ac = new ClassPathXmlApplicationContext("applicationContext.xml");
        final RedisExample redisExample = ac.getBean(RedisExample.class);

        redisExample.dataAccess.set("abc", Arrays.asList(new User(111, "abc", 1)));
        List<User> users = redisExample.dataAccess.get("abc");

        System.out.println(users);
        System.out.println(redisExample.dataAccess.get("abc"));


        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        //System.out.println(redisExample.dataAccess.get("abc"));
                        System.out.println(redisExample.process());

                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
