package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    StringRedisTemplate  stringRedisTemplate;

    @Autowired
    CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //Shop shop = cacheClient.queryWithPathThrough("cache:shop:",id,Shop.class,this::getById,2L,TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithLogicalExpire("cache:shop:",id,Shop.class,this::getById,20L,TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }



//    public Shop queryWithMutex(Long id) {
//        String shopJson= stringRedisTemplate.opsForValue().get("cache:shop:"+id);
//        Shop shop=new Shop();
//        if(StrUtil.isNotBlank(shopJson)){
//            shop=JSONUtil.toBean(shopJson,Shop.class);
//            return shop;
//        }
//
//        if(shopJson!=null){
//            return null;
//        }
//        String lockKey="lock:shop:"+id;
//
//        try {
//            Boolean lock = tryLock(lockKey);
//            if (!lock) {
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//
//            shop = getById(id);
//            Thread.sleep(200);
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", 2, TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), 30, TimeUnit.MINUTES);
//        }catch (InterruptedException e){
//            throw new RuntimeException(e);
//        }finally {
//            endLock(lockKey);
//        }
//
//        return shop;
//    }



    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("ID为空");
        }
        updateById(shop);
        stringRedisTemplate.delete("cache:shop:"+id);
        return Result.ok();
    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        Thread.sleep(100);
        Shop shop=getById(id);
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set("cache:shop:"+id,JSONUtil.toJsonStr(redisData));
    }
}
