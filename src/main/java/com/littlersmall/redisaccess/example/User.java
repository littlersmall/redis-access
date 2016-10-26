package com.littlersmall.redisaccess.example;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by littlersmall on 16/6/9.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class User {
    long userId;
    String name;

    long test;
}
