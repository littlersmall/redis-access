package com.littlersmall.redisaccess;

import com.littlersmall.redisaccess.common.Constants;
import com.littlersmall.redisaccess.common.DetailRes;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Created by littlersmall on 16/5/10.
 */
//带两把锁的数据处理封装
public class RedisMultiDataProcessWithLockBuilder {
    private DataAccess<String> dataAccess;

    public RedisMultiDataProcessWithLockBuilder(RedisTemplate<String, String> redisTemplate) {
        RedisAccessBuilder<String> redisAccessBuilder = new RedisAccessBuilder<>(redisTemplate);

        dataAccess = redisAccessBuilder.buildDataAccess(String.class, null);
    }

    public <T> DataProcessWithDoubleLock<T> buildDataProcessWithDoubleLock(final MultiDataProcess<T> multiDataProcess) {
        return new DataProcessWithDoubleLock<T>() {
            @Override
            public DetailRes execute(String firstKey, String secondKey, T data) {
                String firstLockValue = "" + System.currentTimeMillis();
                String secondLockValue = "" + System.currentTimeMillis();

                try {
                    if (dataAccess.tryLock(firstKey, firstLockValue, Constants.MAX_LOCK_TIME)
                            && dataAccess.tryLock(secondKey, secondLockValue, Constants.MAX_LOCK_TIME)) {
                        return multiDataProcess.process(firstKey, secondKey, data);
                    } else {
                        return new DetailRes(false, firstKey + " and " + secondKey + "lock failed");
                    }
                } finally {
                    dataAccess.unLock(firstKey);
                    dataAccess.unLock(secondKey);
                }
            }
        };
    }
}
