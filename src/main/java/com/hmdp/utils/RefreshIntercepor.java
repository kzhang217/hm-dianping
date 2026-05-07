package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshIntercepor implements HandlerInterceptor {


    StringRedisTemplate stringRedisTemplate;

    public RefreshIntercepor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response, Object handler) throws Exception {

        String token=request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            response.setStatus(401);
            return true;
        }
        Map<Object,Object> map=stringRedisTemplate.opsForHash().entries("login:token:"+token);

        if(map.isEmpty()){
            response.setStatus(401);
            return true;
        }
        UserDTO userDTO=BeanUtil.fillBeanWithMap(map,new UserDTO(),false);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire("login:token:"+token,30, TimeUnit.MINUTES);
        return  true;

    }


}
