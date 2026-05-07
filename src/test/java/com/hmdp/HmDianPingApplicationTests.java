package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    ShopServiceImpl shopService;

    @Resource
    CacheClient cacheClient;
    @Test
    public void test() throws InterruptedException {
        Shop shop =shopService.getById(1L);
        cacheClient.setWithLogicalExpire("cache:shop:"+1L,shop,10L, TimeUnit.SECONDS);


    }

}
