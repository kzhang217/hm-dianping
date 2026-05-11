package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private  UserServiceImpl userServiceImpl;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    IFollowService followService;
    @Override
    public Result queryById(Long id) {
        Blog blog = baseMapper.selectById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        Long userId = blog.getUserId();
        User user=userServiceImpl.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userid= UserHolder.getUser().getId();
        String key="blog:like:"+id;
        Double score =stringRedisTemplate.opsForZSet().score(key, userid.toString());
        if(score==null){
            boolean isSuccess=update().setSql("liked=liked+1").eq("id",id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userid.toString(),System.currentTimeMillis());
            }
        }else{
            boolean isSuccess=update().setSql("liked=liked-1").eq("id",id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userid.toString());
            }

        }

        return Result.ok();
    }

    @Override
    public Result queryHotById(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key="blog:like:"+id;
        Set<String> top5=stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null){
         return Result.ok(Collections.emptyList());
        }

        List<Long> list=top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr= StrUtil.join(",",list);
        List<UserDTO> userDTOS=userService.query().in("id",list).last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream().map(user->BeanUtil.copyProperties(user,UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user=UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSuccess=save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
       List<Follow>  list=followService.query().eq("follow_user_id",user.getId()).list();
        for(Follow follow:list){
            Long userId=follow.getUserId();
            String key="feed:"+follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId=UserHolder.getUser().getId();
        String key="feed:"+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples=
                stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key,0,max,offset,2);
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int ofset=1;
        for(ZSetOperations.TypedTuple<String> typedTuple:typedTuples){
            ids.add(Long.parseLong(typedTuple.getValue()));
            long time= typedTuple.getScore().longValue();
            if(time==minTime){
                ofset++;
            }else{
                minTime=time;
                ofset=1;
            }
        }
        String idStr=StrUtil.join(",",ids);
        List<Blog> blogs=query().in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list();

        for(Blog blog:blogs){
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        ScrollResult scrollResult=new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(ofset);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    public void isBlogLiked(Blog blog){
        UserDTO userDTO=UserHolder.getUser();
        if(userDTO==null){
            return;
        }

        Long userid= UserHolder.getUser().getId();
        String key="blog:like:"+blog.getId();
        Double score=stringRedisTemplate.opsForZSet().score(key, userid.toString());
        blog.setIsLike(score!=null);


    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
