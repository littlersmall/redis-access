package com.littlersmall.redisaccess;


import com.littlersmall.redisaccess.common.DetailRes;

/**
 * Created by littlersmall on 16/5/10.
 */
//以先后顺序锁住firstKey,secondKey,之后执行数据处理
public interface DataProcessWithDoubleLock<T> {
    DetailRes execute(String firstKey, String secondKey, T data);
}
