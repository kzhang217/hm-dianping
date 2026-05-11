package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sentCode(String phone, HttpSession session) {

        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        String code=RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set("login:code:"+phone,code,2, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        String cacheCode=stringRedisTemplate.opsForValue().get("login:code:"+phone);
        String code=(String)loginForm.getCode();

        if(cacheCode==null||!cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }

        User user=query().eq("phone",phone).one();
        if(user ==null){
            user=createUserWithPhone(phone);
        }

        String token=UUID.randomUUID().toString();
        UserDTO userDTO=BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map=BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fieldNamel,fieldValue)->fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll("login:token:"+token,map);
        //stringRedisTemplate.expire("login:token:"+token,30, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId= UserHolder.getUser().getId();
        LocalDateTime localDate = LocalDateTime.now();

        String keySuffix=localDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key="sign:"+userId+keySuffix;

        int dayOfMonth=localDate.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);

        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId= UserHolder.getUser().getId();
        LocalDateTime localDate = LocalDateTime.now();
        String keySuffix=localDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key="sign:"+userId+keySuffix;
        int dayOfMonth=localDate.getDayOfMonth();
        List<Long> sign=stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        if(sign==null||sign.isEmpty()){
            return Result.ok(0);
        }
        Long num=sign.get(0);
        if(num==0||num==null){
            return Result.ok(0);
        }

        int count =0;
        while(true){
            if((num&1)==0){
                break;
            }else{
                count++;
            }
            num>>>=1;

        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName("user:"+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
