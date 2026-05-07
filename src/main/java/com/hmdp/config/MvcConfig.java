package com.hmdp.config;

import com.hmdp.utils.LoginIntercepor;
import com.hmdp.utils.RefreshIntercepor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginIntercepor()).excludePathPatterns("/user/code",
                "/user/login","/blog/hot","/shop/**","/shop-type/**","/upload/**","/voucher/**").order(1);
        registry.addInterceptor(new RefreshIntercepor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }


}
