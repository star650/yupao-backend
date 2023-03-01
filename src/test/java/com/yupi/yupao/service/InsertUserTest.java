package com.yupi.yupao.service;

import com.yupi.yupao.mapper.UserMapper;
import com.yupi.yupao.model.domain.User;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class InsertUserTest {

    @Resource
    private UserService userService;

    /**
     * 循环插入用户
     */
//    @Scheduled(initialDelay = 5000,fixedRate = Long.MAX_VALUE )
   @Test
   public void doInsertUser() {
       System.out.println("测试插入数据");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        final int INSERT_NUM = 1000;
        List<User> userList = new ArrayList<>();

        for (int i = 0; i < INSERT_NUM; i++) {
            User user = new User();
            user.setUsername("假沙鱼");
            user.setUserAccount("yusha");
            user.setAvatarUrl("https://img.youxigt.com/file/2018-09-13/e3aa21ab5befa00a03d6073a6b4ffed2.jpg");
            user.setProfile("一条咸鱼");
            user.setGender(0);
            user.setUserPassword("12345678");
            user.setPhone("123456789108");
            user.setEmail("shayu-yusha@qq.com");
            user.setUserStatus(0);
            user.setUserRole(0);
            user.setPlanetCode("931");
            user.setTags("[]");
            userList.add(user);
        }
//        使用saveBatch是N条sql语句，一次提交。
//        下面的意思是，userList中数据，每100条数据，和数据库建立连接进行提交
        userService.saveBatch(userList,100);
        stopWatch.stop();
        System.out.println(stopWatch.getTotalTimeMillis());

   }
}
