package com.yupi.yupao.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.service.UserService;
import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PreCacheJob {

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    //    引入redission
    @Resource
    private RedissonClient redissonClient;

    // 重点用户,因为用户太多，不可能对所有用户进行预热，预热时间不能太长
    // 自己测试的话，就直接写固定用户即可
    private List<Long> mainUserList = Arrays.asList(1L);

    // 每天执行，预热推荐用户
    @Scheduled(cron = "0 5 18 * * *")   //自己设置时间测试
    public void doCacheRecommendUser() {
//          创建锁
        RLock lock = redissonClient.getLock("yupao:job:precachejob:lock");

        try {
      // 第一个参数表示等待时间，即每个线程的等待时间，对应每个线程只抢一次，抢不到就放弃
        /*
        第二个参数表示锁的有效时间，超过这个时间锁就会过期，当为-1时，
        就会启动redisson 中提供的续 期机制---》看门狗机制，就会自动续期；
         */
            //  if中的代码就是要加分布式锁的代码块-----》缓存预热
            if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
//                输出拿到锁的线程的id
                System.out.println("getLock: " + Thread.currentThread().getId());
                //查数据库
                QueryWrapper<User> queryWrapper = new QueryWrapper<>();
                Page<User> userPage = userService.page(new Page<>(1, 20), queryWrapper);
                String redisKey = String.format("shayu:user:recommend:%s", mainUserList);
                ValueOperations valueOperations = redisTemplate.opsForValue();
                //写缓存,30s过期
                try {
                    valueOperations.set(redisKey, userPage, 30000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    log.error("redis set key error", e);
                }
            }
        } catch (Exception e) {
            log.error("doCacheRecommendUser error", e);
        } finally {
    // 只能释放自己的锁，lock.isHeldByCurrentThread()就是判断是否是自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }
    }

}