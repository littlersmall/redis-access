package com.littlersmall.redisaccess;

/**
 * Created by littlersmall on 16/5/10.
 */
//从数据库中获得数据
public interface DbDataGet<T> {
    T get(String key);
}
