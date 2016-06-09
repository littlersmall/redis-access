package com.littlersmall.redisaccess;

import com.littlersmall.redisaccess.common.DetailRes;

/**
 * Created by littlersmall on 16/5/7.
 */
//锁住key后执行数据处理
public interface DataProcessWithLock<T> {
    DetailRes execute(String key, T data);
}
