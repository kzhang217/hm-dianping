package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IUserService userService;
    @Override
    public Result follow(Long id, boolean isFollow) {

        Long userId = UserHolder.getUser().getId();
        if(isFollow){
            Follow follow = new Follow();

            follow.setFollowUserId(id);
            follow.setUserId(userId);
            boolean isSuccess=save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add("follows:"+userId,String.valueOf(id));
            }

        }else{
            boolean isSuccess =remove(new QueryWrapper<Follow>().eq("follow_user_id", id).eq("user_id", userId));
            if(isSuccess){
                 stringRedisTemplate.opsForSet().remove("follows:"+userId,String.valueOf(id));
            }
        }


        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        int count=query().eq("follow_user_id", id).eq("user_id", userId).count().intValue();
        return Result.ok(count>0);
    }

    @Override
    public Result common(Long id) {
        Long userId = UserHolder.getUser().getId();
        Set<String> commonFollow =stringRedisTemplate.opsForSet().intersect("follows:"+userId,"follows:"+id);
        if(commonFollow==null){
            return Result.ok(Collectors.toList());
        }
        List<Long> list=commonFollow.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOs =userService.listByIds(list).stream().map(user-> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }
}
