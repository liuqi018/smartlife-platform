package com.smartlife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.Follow;
import com.smartlife.mapper.FollowMapper;
import com.smartlife.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.service.IUserService;
import com.smartlife.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
 @Resource
 private StringRedisTemplate stringRedisTemplate;
 @Resource
 private IUserService userService;
    //关注和取消用户
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key= "follows:"+userId;
        //1.判断到底是关注还是取关
        if(isFollow){
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                //把关注用户的id,放入redis中的set集合 sadd  userId followerUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }
        else{
            //3.取关，删除数据 delete tb_follow where userId=? and follow_user_id=?
            //QueryWrapper用来拼接where条件的
            boolean iSremove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id",
                    followUserId));
            //关注的用户移除
           if(iSremove){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }
    //判断是否已经关注
    //是的，这个接口就是专门返回给前端，用来做页面渲染和按钮状态控制的。
    @Override
    public Result isFollow(Long followUserId) {
        //1.获取登录用户
         Long userId=UserHolder.getUser().getId();
         //2.查询是否关注 select count(*) from tb_follow where user_id=? and follow_user_id=? 只需要返回有没有就行
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //返回数据的时候用one 或者收集到集合中
        //3.判断
        return Result.ok(count>0);
    }

    //共同关注
    @Override
    public Result followCommons(Long id) {
    //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //当前用的信息作为第一个集合的key
        String key="follows:"+userId;
        //目标用户也就是你想查看你和谁的共同用户
        String key2="follows:"+id;
        //两个集合求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        //解析出来转换成Long
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
