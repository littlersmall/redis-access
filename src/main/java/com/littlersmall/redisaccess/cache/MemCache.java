package com.littlersmall.redisaccess.cache;

import com.littlersmall.redisaccess.DataAccess;
import com.littlersmall.redisaccess.DbDataGet;
import com.littlersmall.redisaccess.RedisAccessBuilder;
import com.littlersmall.redisaccess.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by littlersmall on 16/5/26.
 */
// 基于hashMap构建的内存缓存
// 数据从redis中获取
// 如果redis中没有则从数据库中获取
// 每隔一定时间同步内存,redis,数据库中的数据,以数据库中的数据为准
@Slf4j
public class MemCache<T> {
    private Map<String, T> map = new ConcurrentHashMap<String, T>();
    private DataAccess<T> dataAccess;
    private DbDataGet<T> dbDataGet;

    public MemCache(RedisTemplate<String, T> redisTemplate, Class<T> clazz, final DbDataGet<T> dbDataGet) {
        RedisAccessBuilder<T> redisAccessBuilder = new RedisAccessBuilder<>(redisTemplate);
        dataAccess = redisAccessBuilder.buildDataAccess(clazz, dbDataGet);
        this.dbDataGet = dbDataGet;

        asynUpdateData();
    }

    //1 检查内存
    //2 查询redis(redis没有则查询数据库)
    public T get(String key) {
        //1
        T value = map.get(key);

        if (null == value) {
            //2
            value = dataAccess.get(key);
            map.put(key, value);
        }

        return value;
    }

    //异步更新数据,主要针对数据库中的数据被修改,造成和redis及内存不一致的情况
    //1 创建新的map
    //2 从原map中遍历数据
    //3 从数据库中获取数据,判断是否和内存中一致
    //4 如果不一致,则更新内存及redis中的数据
    //5 将新的map作为当前的map
    private void asynUpdateData() {
        ExecutorService service = Executors.newFixedThreadPool(1);

        service.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int times = 0;

                    try {
                        //1
                        Map<String, T> newMap = new ConcurrentHashMap<>();

                        //2
                        for (String key : map.keySet()) {
                            times++;

                            //3
                            if (null != dataAccess.get(key)) {
                                T value = dbDataGet.get(key);

                                //4
                                if (value != map.get(key)) {
                                    newMap.put(key, value);
                                    dataAccess.set(key, value, Constants.ONE_DAY);
                                }
                            }

                            //每100条控制一下时间,避免数据库压力过大
                            if (times % 100 == 0) {
                                Thread.sleep(100);
                            }
                        }

                        //5
                        map = newMap;

                        Thread.sleep(Constants.FIVE_MINUTES);
                    } catch (InterruptedException e) {
                        log.info("interrupted " + e);
                    } catch (Exception e) {
                        log.info("exception " + e);
                    }
                }
            }
        });
    }
}
