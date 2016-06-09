package com.littlersmall.redisaccess;


import com.littlersmall.redisaccess.common.DetailRes;

/**
 * Created by littlersmall on 16/5/9.
 */
//数据处理接口
public interface DataProcess<T> {
    DetailRes process(String key, T data);
}
