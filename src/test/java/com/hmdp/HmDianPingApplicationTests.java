package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    ShopServiceImpl shopService;

    @Resource
    CacheClient cacheClient;

    @Resource
    RedisIdWorker redisIdWorker;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    ExecutorService es= Executors.newFixedThreadPool(500);

//    @Test
//    void setRedisIdWorker() throws InterruptedException {
//         CountDownLatch Latch=new CountDownLatch(300);
//        Runnable task=()->{
//            for(int i=0;i<100;i++){
//                long id=redisIdWorker.nextId(   "order");
//                System.out.println("Id:"+ id);
//            }
//            Latch.countDown();
//        };
//        long begin=System.currentTimeMillis();
//        for(int i=0;i<300;i++){
//            es.execute(task);
//        }
//        Latch.await();
//        long end=System.currentTimeMillis();
//        System.out.println("time="+(end-begin));
//    }


//    @Test
//    public void test() throws InterruptedException {
//        Shop shop =shopService.getById(1L);
//        cacheClient.setWithLogicalExpire("cache:shop:"+1L,shop,10L, TimeUnit.SECONDS);
//
//
//    }
//
//    @Test
//    void loadData(){
//        List<Shop> shops=shopService.list();
//        Map<Long,List<Shop>> shopMap=shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
//
//        for(Map.Entry<Long,List<Shop>> entry:shopMap.entrySet()){
//            Long typeId=entry.getKey();
//            List<Shop> list=entry.getValue();
//            String key="shop:geo:"+typeId;
//            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(list.size());
//
//            for(Shop shop:list){
//                //stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
//                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
//            }
//
//            stringRedisTemplate.opsForGeo().add(key,locations);
//
//        }
//
//    }

    @Test
    void testHyperLogLog(){

        String[] values=new String[1000];
        int j=0;
        for (int i=0;i<1000000;i++){
            j=i%1000;
            values[j] = "user" + i;
            if(j==999){
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }

       Long  count=stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(count);

    }

}
