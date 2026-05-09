package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.SimpleRedisLock;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    ISeckillVoucherService seckillVoucherService;

    @Autowired
    RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀券未开始");
        }

        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀券已结束");
        }


        Long userId = UserHolder.getUser().getId();
            SimpleRedisLock lock=new SimpleRedisLock(stringRedisTemplate,"order:"+userId);
            boolean isLock=lock.tryLock(1200);
            if(!isLock){
                return  Result.fail("用户已下单");

            }
            try {
                IVoucherOrderService proxy=(IVoucherOrderService)AopContext.currentProxy();
                return  proxy.createVoucherOrder(voucherId);
            }finally {
                lock.unlock();
            }


    }

    @Transactional
    public  Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

            int count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();

            if (count > 0) {
                return Result.fail("用户已购买");
            }

            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();

            if (!success) {
                return Result.fail("秒杀券已售空");
            }

            VoucherOrder voucherOrder = new VoucherOrder();

            Long id = redisIdWorker.nextId("order");
            voucherOrder.setId(id);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);

            save(voucherOrder);

            return Result.ok(id);

    }
}
