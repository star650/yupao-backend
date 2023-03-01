package com.yupi.yupao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupao.common.ErrorCode;
import com.yupi.yupao.exception.BusinessException;
import com.yupi.yupao.mapper.TeamMapper;
import com.yupi.yupao.model.domain.Team;
import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.model.domain.UserTeam;
import com.yupi.yupao.model.domain.dto.TeamQuery;
import com.yupi.yupao.model.domain.enums.TeamStatusEnum;
import com.yupi.yupao.model.domain.request.TeamJoinRequest;
import com.yupi.yupao.model.domain.request.TeamQuitRequest;
import com.yupi.yupao.model.domain.request.TeamUpdateRequest;
import com.yupi.yupao.model.domain.vo.TeamUserVO;
import com.yupi.yupao.model.domain.vo.UserVO;
import com.yupi.yupao.service.TeamService;
import com.yupi.yupao.service.UserService;
import com.yupi.yupao.service.UserTeamService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 *队伍相关业务，操作team表，队伍的增删改查
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
    implements TeamService{


        @Resource
         UserService userService;

        //这个service操作 用户队伍关系表
        @Resource
        UserTeamService userTeamService;

//        分布式锁
        @Resource
        private RedissonClient redissonClient;

    /**
     * 创建队伍
     * @param team
     * @param loginUser
     * @return
     */
        @Transactional(rollbackFor = Exception.class)
        @Override
        public long addTeam(Team team, User loginUser) {
            //1. 请求参数是否为空？
            if (team == null){
                throw  new BusinessException(ErrorCode.NULL_ERROR);
            }
            //2. 是否登录，未登录不允许创建
            if (loginUser == null) {
                throw  new BusinessException(ErrorCode.NOT_LOGIN);
            }
            final long userId = loginUser.getId();
            //3. 校验信息
            //  a. 队伍人数 > 1 且 <= 20
            Integer maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
            if (maxNum < 1 || maxNum >20){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"队伍人数不符合要求");
            }
            //  b. 队伍标题 <= 20
            String name = team.getName();
            if (StringUtils.isBlank(name) || name.length() > 20) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍标题不满足要求");
            }
            //  c. 队伍描述 <= 512
            String description = team.getDescription();
            if (StringUtils.isNotBlank(description) && description.length() > 512) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍描述过长");
            }
            //  d. status 是否公开（int）不传默认为 0（公开） 队伍状态
//            下面这句代码表示如果队伍状态为null就默认设置为0
            int status = Optional.ofNullable(team.getStatus()).orElse(0);
//            专门给队伍状态 status 创建了枚举类，并在枚举类中定义了该方法获取枚举值
            TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
            if (statusEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态不满足要求");
            }
            //  e. 如果 status 是加密状态，一定要有密码，且密码 <= 32
            String password = team.getPassword();
            if (TeamStatusEnum.SECRET.equals(statusEnum)) {
                if (StringUtils.isBlank(password) || password.length() > 32) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码设置不正确");
                }
            }
            //  f. 队伍的超时时间，超时时间 > 当前时间
            Date expireTime = team.getExpireTime();
            if (new Date().after(expireTime)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "超时时间 > 当前时间");
            }
            //  g. 校验用户最多创建 5 个队伍，在QueryWrapper<>泛型中声明要操作哪个表
            // todo 有 bug，可能同时创建 100 个队伍
            QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId", userId);
            long hasTeamNum = this.count(queryWrapper);
            if (hasTeamNum >= 5) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户最多创建 5 个队伍");
            }

            /**
             * 插入队伍信息到队伍表 和  插入  用户和队伍关系  到关系表
             * 这两个操作必须是同一个事务操作，即要么都成功，要么都失败，
             * 因此该接口用到了@Transactional注解
             */

            //4. 插入队伍信息到队伍表
//            让它自己生成id
            team.setId(null);
            team.setUserId(userId);
            boolean result = this.save(team);
            Long teamId = team.getId();
            if (!result || teamId == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
            }
            //5. 插入  用户和队伍关系  到关系表
            UserTeam userTeam = new UserTeam();
            userTeam.setUserId(userId);
            userTeam.setTeamId(teamId);
            userTeam.setJoinTime(new Date());
            result = userTeamService.save(userTeam);
            if (!result) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
            }
            return teamId;
        }

    /**
     *  查询队伍信息
     *  其中只有管理员才能查看加密还有非公开的队伍，所以我们需要从请求中获得是否为管理员
     */

    @Override
    public List<TeamUserVO> listTeams(TeamQuery teamQuery,boolean isAdmin) {

        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        //组合查询条件
        if (teamQuery != null) {
//            根据id查询
            Long id = teamQuery.getId();
            if (id != null && id > 0) {
                queryWrapper.eq("id", id);
            }
//            根据id列表进行查询队伍信息
            List<Long> idList = teamQuery.getIdList();
            if (CollectionUtils.isNotEmpty(idList)){
                queryWrapper.in("id",idList);
            }



//在TeamQuery新建一个属性：searchText（同时对队伍名称和描述搜索匹配）

            String searchText = teamQuery.getSearchText();
            //queryWrapper 默认以and的方式不断的叠加查询条件，
// 这个需求应当也是以and的方式叠加上，在and里面再写一个 子queryWrapper
// searchText同时对队伍名称和队伍描述进行匹配
            if (StringUtils.isNotBlank(searchText)) {
                queryWrapper.and(qw -> qw.like("name", searchText).or().like("description", searchText));
            }
//           根据队伍名称查询
            String name = teamQuery.getName();
            if (StringUtils.isNotBlank(name)) {
                queryWrapper.like("name", name);
            }
//            根据队伍描述查询
            String description = teamQuery.getDescription();
            if (StringUtils.isNotBlank(description)) {
                queryWrapper.like("description", description);
            }
//            根据最大人数查询
            Integer maxNum = teamQuery.getMaxNum();
            //查询最大人数相等
            if (maxNum != null && maxNum > 0) {
                queryWrapper.eq("maxMum", maxNum);
            }
            Long userId = teamQuery.getUserId();
            //根据创建人来查询
            if (userId != null && userId > 0) {
                queryWrapper.eq("userId", userId);
            }
            //根据状态来查询
            Integer status = teamQuery.getStatus();
//            把数字转为枚举值
            TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
//            如果没有符合的枚举值，状态默认为公开
            if (statusEnum == null) {
                statusEnum = TeamStatusEnum.PUBLIC;
            }
//            如果查询者不是管理员，并且要插询的队伍状态非公开，就抛异常，没有权限
//            只有管理员才能查看加密还有非公开的队伍
            if (!isAdmin && statusEnum.equals(TeamStatusEnum.PRIVATE)) {
                throw new BusinessException(ErrorCode.NO_AUTH);
            }
            queryWrapper.eq("status", statusEnum.getValue());
        }

        //queryWrapper 默认以and的方式不断的叠加查询条件，
// 不展示已过期的队伍，这个需求应当也是以and的方式叠加上，在and里面再写一个 子queryWrapper
// 进行已过期队伍的判断  过期时间 小于当前时间 或者 过期时间为 null，
        //expireTime is null or expireTime > now()   gt:greaterthan
        queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));

//        开始查询,得到一个队伍列表
        List<Team> teamList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }
//        新建一个ArrayList存放查询的所有队伍信息，等会return给前端
        List<TeamUserVO> teamUserVOList = new ArrayList<>();

        //关联查询创建人的用户信息
        for (Team team : teamList) {
//            拿到创建人ID
            Long userId = team.getUserId();
            if (userId == null) {
                continue;
            }
//            这个应该是userService继承的接口里面的方法,根据id获取用户,自己没写这个方法啊.....
            User user = userService.getById(userId);
//            新建一个 队伍和用户信息包装类 对象
            TeamUserVO teamUserVO = new TeamUserVO();
//            给这个对象的一些字段赋值
            BeanUtils.copyProperties(team, teamUserVO);
            //脱敏 创建人信息
            if (user!=null){
//           脱敏就是屏蔽一些敏感字段,不返回给前端
// 因为用户包装类是没有那些敏感字段的,所以只需要把查询出的用户信息赋值给 用户包装类,
// 再返回给前端用户包装类的信息,就实现脱敏功能了
//                 新建 用户包装类 对象,
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
//                脱敏之后,记得给 队伍和用户信息包装类 的创建人属性赋值
                teamUserVO.setCreateUser(userVO);
            }
//            查询出的 队伍和用户信息包装类 信息存放在arraylist中，返回给前端
            teamUserVOList.add(teamUserVO);
        }
        return teamUserVOList;
    }

    /**
     * 修改队伍信息
     * @param teamUpdateRequest
     * @param loginUser
     * @return
     */
    @Override
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = teamUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
//        根据Team ID查询队伍
        Team oldTeam = this.getById(id);
        if (oldTeam==null){
            throw new BusinessException(ErrorCode.NULL_ERROR,"队伍不存在");
        }
        //只有管理员或者队伍的创建者才可以修改
        if (oldTeam.getUserId()!=loginUser.getId()&&!userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

//        如果队伍状态改为加密，必须要有密码
//        获取要修改成的队伍状态
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        if (statusEnum.equals(TeamStatusEnum.SECRET)){
//            如果没有传递密码
            if (StringUtils.isBlank(teamUpdateRequest.getPassword())){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"加密房间必须要设置密码");
            }
        }

//        开始修改信息
        Team updateTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest,updateTeam);
        return this.updateById(updateTeam);
    }

    /**
     * 用户加入队伍
     * @param teamJoinRequest
     * @param loginUser
     * @return
     */
    @Override
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
//      获取队伍id，判断参数id是否为空
        Long teamId = teamJoinRequest.getTeamId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
//        根据队伍id查询队伍，判断队伍是否存在，以及是否过期
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍不存在");
        }
        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已过期");
        }
//        获取队伍状态，判断队伍是否允许加入
        Integer status = team.getStatus();
//        根据传来的数字获取状态枚举值
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
        if (teamStatusEnum.PRIVATE.equals(teamStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止加入私有队伍");
        }
//       获取密码， 如果是加密队伍则需要输入密码
        String password = teamJoinRequest.getPassword();
        if (teamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || !password.equals(team.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
            }
        }
        //该用户已加入的队伍数量 数据库查询所以放到下面，减少查询时间
        Long userId = loginUser.getId();

        // 只有一个线程能获取到锁，每一个锁都有自己的名字
        RLock lock = redissonClient.getLock("yupao:join_team");

        try {
            // 抢到锁并执行，使用while(true)就是为了，如果有的线程没有拿到锁就继续抢锁，一直到所有的线程都抢到锁执行，结束循环
            while (true) {
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                    System.out.println("getLock: " + Thread.currentThread().getId());
                    QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", userId);
                    long hasJoinNum = userTeamService.count(userTeamQueryWrapper);
//        最多加入5个队伍
                    if (hasJoinNum >= 5) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入5个队伍");
                    }
                    //不能重复加入已加入的队伍，这里查询的是用户队伍关系表user_team
                    userTeamQueryWrapper = new QueryWrapper<>();
//        下面这两个查询条件是 且 的关系
                    userTeamQueryWrapper.eq("userId", userId);
                    userTeamQueryWrapper.eq("teamId", teamId);
                    long hasUserJoinTeam = userTeamService.count(userTeamQueryWrapper);
                    if (hasUserJoinTeam > 0) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户已加入该队伍");
                    }

//        不能加入已经满了的队伍，这里查询的是用户队伍关系表user_team
                    //已加入队伍的人数
                    userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("teamId", teamId);
                    long teamHasJoinNum = userTeamService.count(userTeamQueryWrapper);
                    if (teamHasJoinNum >= team.getMaxNum()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
                    }
//        如果都满足上述条件，开始插入数据
                    //修改队伍信息
                    UserTeam userTeam = new UserTeam();
                    userTeam.setUserId(userId);
                    userTeam.setTeamId(teamId);
                    userTeam.setJoinTime(new Date());
                    return userTeamService.save(userTeam);
                }
            }
        }catch (InterruptedException e){
            log.error("doCacheRecommendUser error", e);
            return false;
        }finally {
            // 只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }


    }

    /**
     * 用户退出队伍
     * @param teamQuitRequest
     * @param loginUser
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
//        判空
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamQuitRequest.getTeamId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
//        获取队伍ID，查询队伍是否存在
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
//        获取用户ID，创建一条用户和队伍关系的数据，
        long userId = loginUser.getId();
        UserTeam queryUserTeam = new UserTeam();
        queryUserTeam.setTeamId(teamId);
        queryUserTeam.setUserId(userId);
//   查询这条数据是否存在在用户关系表，如果存在，代表用户加入了这个队伍，
//   反之，用户未加入，未加入也就不可能退出了
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>(queryUserTeam);
        long count = userTeamService.count(queryWrapper);
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未加入队伍");
        }
//        通过队伍ID获取某队伍当前人数，这时自己在下面封装的方法
        long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
        //队伍只剩下一个人，解散
        if (teamHasJoinNum == 1) {
            //删除队伍
            this.removeById(teamId);
        } else {
            //队伍至少还剩下两人
            //是队长
            if (team.getUserId() == userId) {
                //把队伍转移给最早加入的用户
                //1.查询已加入队伍的所有用户和加入时间
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
//                多个QueryWrapper条件是 且 的关系
                userTeamQueryWrapper.eq("teamId", teamId);
//                查询最小两个id 的 用户，代表最早两个加入这个队伍
//     默认队长是最早加入的，之所以查两个，是为了确认队伍至少还有两个人
                userTeamQueryWrapper.last("order by id asc limit 2");
//                开始查询
                List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
                if (CollectionUtils.isEmpty(userTeamList) || userTeamList.size() <= 1) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
//     默认队长是最早加入的，所以取第二小id的用户作为新的队长，0,1
                UserTeam nextUserTeam = userTeamList.get(1);
//                根据id获取到新的队长信息
                Long nextTeamLeaderId = nextUserTeam.getUserId();
                //更新当前队伍的队长
                Team updateTeam = new Team();
                updateTeam.setId(teamId);
//                更新Team表中 队伍的队长id
                updateTeam.setUserId(nextTeamLeaderId);
                boolean result = this.updateById(updateTeam);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新队伍队长失败");
                }
            }
        }
        //移除 队伍用户关系表中 的这条数据，这个方法是mybatisplus提供的方法
        return userTeamService.remove(queryWrapper);
    }

    /**
     * 队长解散队伍
     * @param id
     * @param loginUser
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(long id, User loginUser) {
        //校验请求参数，controller层已经校验过了
        // 校验队伍是否存在，
        // 根据 id 获取队伍信息，因为重复使用这段代码，自己就单独抽离里出来
        Team team = getTeamById(id);
        long teamId = team.getId();
        // 校验你是不是队伍的队长
        //        //test
        //        System.out.println("team.getUserId():"+team.getUserId().getClass().getName());
        //        System.out.println("loginUser.getId():"+loginUser.getId().getClass().getName());
        if (!team.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH,"无访问权限");
        }
        // 移除所有加入队伍的关联信息，操作user_team表
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        boolean result = userTeamService.remove(userTeamQueryWrapper);
        if (!result){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"删除队伍关联信息失败");
        }
        // 删除队伍
        return this.removeById(teamId);
    }


    /**
     * 获取某队伍当前人数
     *查询用户队伍关系表
     * @param teamId
     * @return
     */
    private long countTeamUserByTeamId(long teamId) {
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        return userTeamService.count(userTeamQueryWrapper);
    }

    /**
     * 根据 id 获取队伍信息
     * @param teamId
     * @return
     */
    private Team getTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        return team;
    }
}













