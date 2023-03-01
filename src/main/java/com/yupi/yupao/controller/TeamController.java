package com.yupi.yupao.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupao.common.BaseResponse;
import com.yupi.yupao.common.ErrorCode;
import com.yupi.yupao.common.ResultUtils;
import com.yupi.yupao.exception.BusinessException;
import com.yupi.yupao.model.domain.Team;
import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.model.domain.UserTeam;
import com.yupi.yupao.model.domain.dto.TeamQuery;
import com.yupi.yupao.model.domain.request.*;
import com.yupi.yupao.model.domain.vo.TeamUserVO;
import com.yupi.yupao.service.TeamService;
import com.yupi.yupao.service.UserService;
import com.yupi.yupao.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;


import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/team")
@CrossOrigin(origins = {"http://localhost:3000"},allowCredentials = "true")
@Slf4j
public class TeamController {

        @Resource
        private UserService userService;

        @Resource
        private RedisTemplate redisTemplate;

        @Resource
        private TeamService teamService;

        @Resource
        private UserTeamService userTeamService;


    /**
     * 创建队伍
     * 对于增加接口，一般传入的参数都是一个实体对象或者封装好的请求体
     * 返回值一般都是long或者Integer类型
     * @param
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request){
        if (teamAddRequest == null){
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        User logininUser = userService.getLoginUser(request);
        Team team = new Team();
//            这个函数，是吧前面的字段的值，赋值给后面的字段
        BeanUtils.copyProperties(teamAddRequest,team);
        long teamId = teamService.addTeam(team,logininUser);
        return ResultUtils.success(teamId);
    }

    /**解散队伍
     * 删除队伍接口，一般都是根据 id 或者 封装的请求体 作为参数进行删除，然后返回一个布尔值
     * @param
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(id, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败");
        }
        return ResultUtils.success(true);
    }

    /**修改队伍信息
     * 修改接口，参数一般是实体对象或者封装类，然后返回一个布尔值
     * @param
     * @return
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest, HttpServletRequest request) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest,loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        return ResultUtils.success(true);
    }

    /**
     * 查询接口，一般传入的参数都是id,返回值一般都是实体对象
     * @param id
     * @return
     */
        @GetMapping("/get")
        public BaseResponse<Team> getTeamById(long id){
            if (id <= 0){
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            Team team = teamService.getById(id);
            if (team == null){
                throw new BusinessException(ErrorCode.NULL_ERROR);
            }
            return ResultUtils.success(team);
        }

    /**
     * 用户加入队伍接口
     * @param teamJoinRequest
     * @param request
     * @return
     */

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request){
        if (teamJoinRequest==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 用户退出队伍接口
     * @param teamQuitRequest
     * @param request
     * @return
     */
    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request){
        if (teamQuitRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);
    }



    /**
     *列表查询，根据teamQuery查询条件，查询队伍
     * @param teamQuery
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery,HttpServletRequest request){
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean isAdmin = userService.isAdmin(request);
        // 1、根据条件查询队伍列表
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin);
//        取所有查询到的队伍的id，形成一个新的列表，
        final List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        // 2、判断当前用户是否已加入队伍
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        try {
            User loginUser = userService.getLoginUser(request);
//            查询在user_team表中，当前用户对应的每一条数据，即当前用户加入的队伍
            userTeamQueryWrapper.eq("userId", loginUser.getId());
//            查询 user_team表中当前用户对应的每一条数据  是否在查询到的队伍列表中
//     只显示 在查询到的队伍列表teamIdList中 当前用户对应的每一条数据（user_team表中）
            userTeamQueryWrapper.in("teamId", teamIdList);
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            // 已加入的队伍 id 集合（user_team表中当前用户对应的每一条数据）
            Set<Long> hasJoinTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
//            把已经加入的队伍的TeamUserVO 的hasJoin设置为true
            teamList.forEach(team -> {
                boolean hasJoin = hasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });
        } catch (Exception e) {}

        // 3、查询已加入队伍的人数
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
//        在根据查询条件查询到的队伍列表teamIdList中，查询队伍列表中每一条数据 对应的user-team表中的数据
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        // 队伍 id => 加入这个队伍的用户列表
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
//      遍历 根据条件查询出的队伍列表 ，设置队伍列表中每一个队伍的已加入用户数
        teamList.forEach(team -> team.setHasJoinNum(teamIdUserTeamList.getOrDefault(team.getId(), new ArrayList<>()).size()));


        return ResultUtils.success(teamList);
    }

    /**
     * 获取我创建的队伍
     *根据teamQuery查询条件，查询队伍
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
//     把teamQuery中的创建人id设置为当前登录用户id，进而进行查询当前用户创建的队伍
        teamQuery.setUserId(loginUser.getId());
//   第二个参数为true是为了保证只要是我创建的，无论我是不是管理员，都能获取
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        return ResultUtils.success(teamList);
    }

    /**
     * 获取我加入的队伍信息
     *根据用户id，查询出用户加入的所有的队伍id，然后再根据所有的队伍id查询出所有的队伍信息
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
//        查询user_team表，根据用户id，查出所有的用户加入的队伍
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);

        // 取出不重复的队伍 id，正常来说，是不会有重复的队伍id的。以防万一，防止脏数据
//        groupingBy(UserTeam::getTeamId))：表示根据teamId进行分组，也就是去重
        // teamId   UserTeam对象
        Map<Long, List<UserTeam>> listMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
//        取出键值的集合，也就是用户加入的所有的队伍id的集合
        List<Long> idList = new ArrayList<>(listMap.keySet());
        teamQuery.setIdList(idList);
//        根据队伍id列表进行查询队伍信息
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        return ResultUtils.success(teamList);
    }

    /**
     *分页查询
      * @param teamQuery
     * @return
     */
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listPageTeams(TeamQuery teamQuery) {
            if (teamQuery == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            Team team = new Team();
//            这个函数，是吧前面的字段的值，赋值给后面的字段
            BeanUtils.copyProperties(teamQuery, team);
            Page<Team> page = new Page<>(teamQuery.getPageNum(),teamQuery.getPageSize());
            QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
            Page<Team> resultPage = teamService.page(page,queryWrapper);
            return ResultUtils.success(resultPage);
        }

    /**
     * 获取最匹配的用户
     *
     * @param num 推荐的用户数量
     * @param request
     * @return
     */
    @GetMapping("/match")
    public BaseResponse<List<User>> matchUsers(long num, HttpServletRequest request) {
        if (num <= 0 || num > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.matchUsers(num, user));
    }


 }
