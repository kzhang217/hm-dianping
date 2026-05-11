package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import javafx.scene.effect.Light;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;


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
        Shop shop = cacheClient.queryWithPathThrough("cache:shop:",id,Shop.class,this::getById,2L,TimeUnit.MINUTES);
        //Shop shop = cacheClient.queryWithLogicalExpire("cache:shop:",id,Shop.class,this::getById,20L,TimeUnit.SECONDS);
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if(x==null || y==null){
            Page<Shop> page=query().eq("type_id",typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return  Result.ok(page);
        }
        int from =(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current*SystemConstants.DEFAULT_PAGE_SIZE;

        String key="shop:geo:"+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );


// 4.解析出id
        if (results == null) {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if(content.size()<=from){
            return Result.ok();
        }

        List<Long> ids=new ArrayList<>();
        Map<String,Distance> distanceMap=new HashMap<>();
        content.stream().skip(from).forEach(result->{
            String shopId= result.getContent().getName();
            ids.add(Long.parseLong(shopId));
            Distance distance = result.getDistance();
            distanceMap.put(shopId,distance);
        });
        String idStr=StrUtil.join(",",ids);
       List<Shop> shops= query().in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list();
       for(Shop shop:shops){
           shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
       }

        return Result.ok(shops);
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
