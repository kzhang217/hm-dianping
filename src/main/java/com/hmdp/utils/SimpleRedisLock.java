package com.hmdp.utils;


import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private static final DefaultRedisScript<Long> unlockScript;
    static {

        unlockScript = new DefaultRedisScript<>();
        unlockScript.setLocation(new ClassPathResource("unlock.lua"));
        unlockScript.setResultType(Long.class);
    }


    StringRedisTemplate stringRedisTemplate;
    String name;
    String prefix="lock:";
    String ID= UUID.randomUUID().toString()+"-";
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;

    }
    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId=ID+Thread.currentThread().getId();
        Boolean success=stringRedisTemplate.opsForValue()
                .setIfAbsent(prefix+name,threadId,timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        Object a=stringRedisTemplate.execute(unlockScript, Collections.singletonList(prefix+name)
                ,ID + Thread.currentThread().getId());

    }


//    @Override
//    public void unlock() {
//        String threadId=ID+Thread.currentThread().getId();
//        String lockId=stringRedisTemplate.opsForValue().get(prefix+name);
//        if(lockId.equals(threadId)){
//            stringRedisTemplate.delete(prefix+name);
//        }
//
//    }
}
