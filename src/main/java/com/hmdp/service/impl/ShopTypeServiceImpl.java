package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryCache() {
        String key = "cache:shopType:1";

        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            List<ShopType> list = JSONUtil.toList(shopJson, ShopType.class);
            return Result.ok(list);
        }

        List<ShopType> shopList = query()
                .orderByAsc("sort")
                .list();

        if (shopList == null || shopList.isEmpty()) {
            return Result.fail("没有数据");
        }

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopList));

        return Result.ok(shopList);
    }
}
