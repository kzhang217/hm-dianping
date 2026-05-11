package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
    RedissonClient redisson;
    @Autowired
    RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //
//    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024*1024);
    private ExecutorService seckill_order_executor = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;

    @PostConstruct
    private void initial() {
        seckill_order_executor.submit(new VoucherOrderTask());
    }


    private class VoucherOrderTask implements Runnable {
        String mq = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1")
                            , StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(mq, ReadOffset.lastConsumed()));

                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(mq, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    try {
                        handlePendingList();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        }

        private void handlePendingList() throws InterruptedException {

            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1")
                            , StreamReadOptions.empty().count(1),
                            StreamOffset.create(mq, ReadOffset.from("0")));

                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(mq, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    Thread.sleep(20);
                }
            }
        }

    }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            long userId = voucherOrder.getUserId();
            RLock lock = redisson.getLock("lock:order:" + userId);
            boolean isLock = lock.tryLock();
            if (!isLock) {
                log.error("不允许重复下单");
                return;
            }
            try {
                proxy.createVoucherOrder(voucherOrder);
            } finally {

            }


        }

        private static final DefaultRedisScript<Long> seckillScript;

        static {

            seckillScript = new DefaultRedisScript<>();
            seckillScript.setLocation(new ClassPathResource("seckill.lua"));
            seckillScript.setResultType(Long.class);
        }

        public Result seckillVoucher(Long voucherId) {
            Long userId = UserHolder.getUser().getId();
            long orderId = redisIdWorker.nextId("order");
            Long result = stringRedisTemplate.execute(seckillScript, Collections.emptyList(),
                    voucherId.toString(), userId.toString(), String.valueOf(orderId));
            int r = result.intValue();
            if (r != 0) {
                return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
            }

            proxy = (IVoucherOrderService) AopContext.currentProxy();
            return Result.ok(orderId);
        }


        @Transactional
        public void createVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();

            int count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .count().intValue();

            if (count > 0) {
                log.error("用户已购买");
                return;
            }

            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0)
                    .update();

            if (!success) {
                log.error("秒杀券已售空");
                return;
            }


            save(voucherOrder);

        }


}