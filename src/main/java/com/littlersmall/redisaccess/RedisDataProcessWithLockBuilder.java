package com.littlersmall.redisaccess;

import com.littlersmall.redisaccess.common.Constants;
import com.littlersmall.redisaccess.common.DetailRes;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Created by littlersmall on 16/5/10.
 */
//带锁的数据处理封装
public class RedisDataProcessWithLockBuilder {
    private DataAccess<String> dataAccess;

    public RedisDataProcessWithLockBuilder(RedisTemplate<String, String> redisTemplate) {
        RedisAccessBuilder<String> redisAccessBuilder = new RedisAccessBuilder<>(redisTemplate);

        dataAccess = redisAccessBuilder.buildDataAccess(String.class, null);
    }

    public <T> DataProcessWithLock<T> buildDataProcessWithLock(final DataProcess<T> dataProcess) {
        return new DataProcessWithLock<T>() {
            @Override
            public DetailRes execute(String key, T data) {
                //value为当前时间,方便追查问题
                String lockValue = "" + System.currentTimeMillis();

                try {
                    if (dataAccess.tryLock(key, lockValue, Constants.MAX_LOCK_TIME)) {
                        return dataProcess.process(key, data);
                    } else {
                        return new DetailRes(false, key + " locked failed");
                    }
                } catch (Exception e) {
                    return new DetailRes(false, key + " " + e.toString());
                } finally {
                    dataAccess.unLock(key);
                }
            }
        };
    }
}
