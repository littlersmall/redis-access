package com.littlersmall.redisaccess;

import java.util.List;

/**
 * Created by littlersmall on 16/5/7.
 */
//数据查询,更新接口
public interface DataAccess<T> {
    void set(String key, T value);

    void set(String key, T value, int validTime);

    T get(String key);

    List<T> popAll(String key);

    void push(String key, T value);

    //validTime = -1 表示无失效时间
    boolean setNX(String key, T value, int validTime);

    void delete(String key);

    boolean tryLock(String key, T value, int timeout);

    void unLock(String key);

    //T 为 int or long 类型可用
    long increment(String key, int validTime);

    //方便测试
    boolean flushDb();
}
