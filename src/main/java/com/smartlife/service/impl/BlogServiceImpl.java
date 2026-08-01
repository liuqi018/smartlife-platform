package com.smartlife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlife.dto.Result;
import com.smartlife.dto.ScrollResult;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.Blog;
import com.smartlife.entity.Follow;
import com.smartlife.entity.User;
import com.smartlife.mapper.BlogMapper;
import com.smartlife.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.service.IFollowService;
import com.smartlife.service.IUserService;
import com.smartlife.utils.RedisConstants;
import com.smartlife.utils.SystemConstants;
import com.smartlife.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogByid(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("博客不存在");
        }
        //2.查询blog有关的用户
        queryBlogUser(blog);
        //3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }
    //分页查询
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }
    private void isBlogLiked(Blog blog) {
        UserDTO user=UserHolder.getUser();
        if(user==null){
            return ;
        }
        //1.获取当前用户 但是不一定有 用户未登录不用获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户有没有点赞   也就是set集合中有没有用户的Id就得先获取当前用户
        String key= RedisConstants.BLOG_LIKED_KEY+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }
   //实现一个人只能改一个笔记点赞一次
    @Override
    public Result likeBlog(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户有没有点赞   也就是set集合中有没有用户的Id就得先获取当前用户
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score= stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //3.如果没有点赞 可以点赞
        if (score==null) {//3.1数据库点赞数加一
            boolean success = update().setSql("liked=liked+1").eq("id", id).update();
            if (success) {//点赞成功了就往Redis中写
                //3.2保存用户到Redis的set集合
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }
        else {//4.如果已经点赞，取消点赞 //4.1数据库点赞数-1
                boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
                if (isSuccess) {
                    //4.2把用户从Redis的set集合移除
                    stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                }
            }
        return Result.ok();
    }
  //点赞排行榜前五名
    @Override
    public Result queryBlogLikes(Long id) {
        String key= RedisConstants.BLOG_LIKED_KEY+id;
        //1.查询top5的点赞用户
        Set<String> userSet = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(userSet==null||userSet.size()==0){
            return Result.ok();
        }
        //2.解析出其中的用户id
        List<Long> ids = userSet.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr= StrUtil.join(",",ids);
        //3.根据用户id查询用户  要让先点赞的排在前面
        List<UserDTO> userDTOS = userService.query()
                //实现的就是WHERE id in (5,1) ORDER BY FIELD(id,5,1)
                .in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回用户信息
        return Result.ok(userDTOS);
    }
    private  void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user=userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    //保存blog到reids和数据库
    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //2.保存探店笔记
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        //3.查询笔记作者的所有粉丝  //select * from tb_follow where follow_user_id=?
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow:followUserId){
            //4.1获取粉丝id
            Long userId = follow.getUserId();
            //4.2 推送给每个粉丝的收件箱 每个收件箱都是一个Zset //4.推送笔记id给所有粉丝
            //设置Zset的key  value就是blog的id
            String key=RedisConstants.FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        //5.返回id
        return Result.ok(blog.getId());
    }
    //粉丝实现滚动式分页查询
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱 //ZREVRANGEBYSCORE key maxTime minTime LIMIT offset count
        String key=RedisConstants.FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                key, 0, max, offset, 3
        );
        //3.非空判断
        if(typedTuples==null||typedTuples.size()==0){
            return Result.ok();
        }
        //4.解析收件箱blogId、时间戳-minTime、offset
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for(ZSetOperations.TypedTuple<String> typedTuple:typedTuples){
            //4.1 获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //  //4.2获取分数-时间戳 并进行offset计数
            long time=typedTuple.getScore().longValue();
            if(time==minTime){
                os++;
            }else{
                minTime=time;
                os=1;//不等于计数器要重置
            }
        }
        //4.根据blogId查询blog
        String idStr= StrUtil.join(",",ids);
        List<Blog> blogs = query()
                //实现的就是WHERE id in (5,1) ORDER BY FIELD(id,5,1)
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for(Blog blog:blogs){
            //5.1 查询blog有关的用户
            queryBlogUser(blog);
            //5.2查询blog是否被点赞
            isBlogLiked(blog);
        }
        //5.封装并返回
        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }
}
