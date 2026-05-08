package com.hmdp.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component

public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1767225600L;

    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long  nextId(String prefix){
        LocalDateTime time=LocalDateTime.now();
        long second =time.toEpochSecond(ZoneOffset.UTC);
        long timestamp =second-BEGIN_TIMESTAMP;

        String data= time.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count =stringRedisTemplate.opsForValue().increment("icr:"+prefix+":"+data);
        return timestamp<<32 |count;

    }


}
