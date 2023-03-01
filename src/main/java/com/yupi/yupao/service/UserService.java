package com.yupi.yupao.service;

import com.yupi.yupao.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务
 *
 * @author yupi
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @param planetCode 星球编号
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword, String planetCode);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    User getSafetyUser(User originUser);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    int userLogout(HttpServletRequest request);

    /**
     * 根据用户标签查询用户
     * @param tagNameList
     * @return
     */
    List<User> searchUsersByTags(List<String> tagNameList);

    /**
     * 修改用户信息
     */
    int updateUser(User user,User loginUser);

    /**
     * 获取当前用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 是否为管理员
     */

     boolean isAdmin(User user);

    /**
     * 是否为管理员,方法重载
     */

    boolean isAdmin(HttpServletRequest request);

    /**
     * 获取最匹配的用户，根据标签匹配用户
     * @param num 推荐的用户数量
     * @param user
     * @return
     */
    List<User> matchUsers(long num, User user);
}
