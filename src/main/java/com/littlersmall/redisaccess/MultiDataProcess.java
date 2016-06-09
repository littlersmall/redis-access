package com.littlersmall.redisaccess;


import com.littlersmall.redisaccess.common.DetailRes;

/**
 * Created by littlersmall on 16/5/10.
 */
public interface MultiDataProcess<T> {
    DetailRes process(String firstKey, String secondK, T data);
}