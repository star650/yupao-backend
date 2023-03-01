package com.yupi.yupao.once;
import java.util.Date;

import com.yupi.yupao.mapper.UserMapper;
import com.yupi.yupao.model.domain.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class InsertUser {

    @Resource
    private UserMapper userMapper;
    /**
     * 批量插入用户
     */
//    @Scheduled(fixedDelay = 5000)
    public void doInsertUser(){
       final int INSER_NUM = 1000;
       for (int i = 0;i < INSER_NUM;i++){
           User user = new User();
           user.setUsername("定时任务"+i);
           user.setUserAccount("yupi"+i);
           user.setAvatarUrl("");
           user.setGender(0);
           user.setUserPassword("");
           user.setPhone("");
           user.setEmail("");
           user.setUserStatus(0);

           user.setIsDelete(0);
           user.setUserRole(0);
           user.setPlanetCode("11111");
           user.setTags("['大一']");

           userMapper.insert(user);

       }


    }

}
