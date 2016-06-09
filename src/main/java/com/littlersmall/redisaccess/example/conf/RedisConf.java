package com.littlersmall.redisaccess.example.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.util.Properties;

/**
 * Created by littlersmall on 16/5/16.
 */
@Configuration
public class RedisConf {
    @Bean
    public JedisPoolConfig buildPoolConfig() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();

        jedisPoolConfig.setMaxTotal(250);
        jedisPoolConfig.setMaxIdle(25);
        jedisPoolConfig.setMaxWaitMillis(5000);

        return jedisPoolConfig;
    }

    @Bean
    public JedisConnectionFactory buildConnectionFactory(JedisPoolConfig jedisPoolConfig) {
        Properties properties = new Properties();

        try {
            Resource res = new ClassPathResource("redis.properties");
            properties.load(res.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rabbitmq.properties!");
        }

        String ip = properties.getProperty("ip");
        int port = Integer.valueOf(properties.getProperty("port"));

        System.out.println("ip is: " + ip + " port is " + port);

        JedisConnectionFactory connectionFactory = new JedisConnectionFactory();

        connectionFactory.setHostName(ip);
        connectionFactory.setPort(port);
        connectionFactory.setPoolConfig(jedisPoolConfig);

        return connectionFactory;
    }

    @Bean
    @Scope("prototype")
    public <String, T> RedisTemplate buildRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, T> redisTemplate = new RedisTemplate<String, T>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        return redisTemplate;
    }
}

