package com.smartlife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlife.dto.Result;
import com.smartlife.dto.ScrollResult;
import com.smartlife.dto.UserDTO;
import com.smartlife.dto.BlogUpdateDTO;
import com.smartlife.entity.Blog;
import com.smartlife.entity.Follow;
import com.smartlife.entity.User;
import com.smartlife.mapper.BlogCollectionMapper;
import com.smartlife.mapper.BlogCommentsMapper;
import com.smartlife.mapper.BlogLikeMapper;
import com.smartlife.mapper.BlogMapper;
import com.smartlife.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.service.IFollowService;
import com.smartlife.service.IShopService;
import com.smartlife.service.IUserService;
import com.smartlife.utils.RedisConstants;
import com.smartlife.utils.SystemConstants;
import com.smartlife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    @Resource
    private IShopService shopService;
    @Resource
    private BlogCollectionMapper blogCollectionMapper;
    @Resource
    private BlogCommentsMapper blogCommentsMapper;
    @Resource
    private BlogLikeMapper blogLikeMapper;

    @Override
    public Result queryBlogByid(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("博客不存在");
        }
        if (!canCurrentUserView(blog)) {
            return Result.fail("博客不存在或无权访问");
        }
        //2.查询blog有关的用户
        queryBlogUser(blog);
        //3.查询blog是否被点赞
        isBlogLiked(blog);
        setCurrentUserState(blog);
        return Result.ok(blog);
    }
    //分页查询
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .eq("visibility", 0)
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

    @Override
    public Result queryMyBlogs(Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        int pageNumber = current == null || current < 1 ? 1 : current;
        Page<Blog> page = query().eq("user_id", user.getId())
                .orderByDesc("create_time").orderByDesc("id")
                .page(new Page<>(pageNumber, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
    private void isBlogLiked(Blog blog) {
        UserDTO user=UserHolder.getUser();
        if(user==null){
            blog.setIsLike(false);
            blog.setLikedByCurrentUser(false);
            return ;
        }
        //1.获取当前用户 但是不一定有 用户未登录不用获取用户id
        Long userId = user.getId();
        //2.判断当前用户有没有点赞   也就是set集合中有没有用户的Id就得先获取当前用户
        String key= RedisConstants.BLOG_LIKED_KEY+blog.getId();
        Double score = null;
        try {
            score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        } catch (Exception e) {
            log.warn("redis blog like cache query failed,blogId={},userId={},errorType={},error={}",
                    blog.getId(), userId, e.getClass().getSimpleName(), e.getMessage());
        }
        boolean liked = score != null;
        if (!liked) {
            liked = blogLikeMapper.countByUserAndBlog(userId, blog.getId()) > 0;
            if (liked) {
                updateLikeCache(key, userId, true, System.currentTimeMillis());
            }
        }
        blog.setIsLike(liked);
        blog.setLikedByCurrentUser(liked);
    }

    private void setCurrentUserState(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            blog.setCollectedByCurrentUser(false);
            blog.setIsOwner(false);
            return;
        }
        blog.setCollectedByCurrentUser(blogCollectionMapper.countByUserAndBlog(user.getId(), blog.getId()) > 0);
        blog.setIsOwner(user.getId().equals(blog.getUserId()));
    }
   //实现一个人只能改一个笔记点赞一次
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result likeBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        Blog target = getById(id);
        if (target == null || !canCurrentUserView(target)) return Result.fail("笔记不存在或无权访问");

        Long userId = user.getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        boolean alreadyLiked = blogLikeMapper.countByUserAndBlog(userId, id) > 0;
        if (!alreadyLiked) {
            int inserted = blogLikeMapper.insertIgnore(userId, id);
            if (inserted == 1 && baseMapper.incrementLiked(id) != 1) {
                throw new IllegalStateException("笔记点赞数更新失败");
            }
            updateLikeCacheAfterCommit(key, userId, true, System.currentTimeMillis());
            return Result.ok();
        }

        int deleted = blogLikeMapper.deleteByUserAndBlog(userId, id);
        if (deleted == 1) {
            baseMapper.decrementLikedSafely(id);
        }
        updateLikeCacheAfterCommit(key, userId, false, 0L);
        return Result.ok();
    }

    private void updateLikeCacheAfterCommit(String key, Long userId, boolean liked, long score) {
        Runnable cacheUpdate = () -> updateLikeCache(key, userId, liked, score);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheUpdate.run();
                }
            });
        } else {
            cacheUpdate.run();
        }
    }

    private void updateLikeCache(String key, Long userId, boolean liked, long score) {
        try {
            if (liked) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), score);
            } else {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } catch (Exception e) {
            log.warn("redis blog like cache update failed,key={},userId={},liked={},errorType={},error={}",
                    key, userId, liked, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Result collectBlog(Long blogId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Blog blog = getById(blogId);
        if (blog == null || !canCurrentUserView(blog)) {
            return Result.fail("博客不存在或无权访问");
        }
        if (blogCollectionMapper.countByUserAndBlog(user.getId(), blogId) > 0) {
            blogCollectionMapper.deleteByUserAndBlog(user.getId(), blogId);
            return Result.ok(false);
        }
        blogCollectionMapper.insertIgnore(user.getId(), blogId);
        return Result.ok(true);
    }

    @Override
    public Result updateBlog(Long blogId, BlogUpdateDTO request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        Blog existing = getById(blogId);
        if (existing == null) return Result.fail("博客不存在");
        if (!user.getId().equals(existing.getUserId())) return Result.fail("无权操作该笔记");
        if (request == null || StrUtil.isBlank(request.getTitle()) || StrUtil.isBlank(request.getContent())) {
            return Result.fail("标题和正文不能为空");
        }
        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        if (title.length() > 255 || content.length() > 2048) return Result.fail("标题或正文过长");
        if (request.getShopId() == null || shopService.getById(request.getShopId()) == null) {
            return Result.fail("关联商户不存在");
        }
        String images = request.getImages() == null ? "" : request.getImages().trim();
        if (images.length() > 2048) return Result.fail("图片地址过长");
        boolean updated = update().set("title", title).set("content", content)
                .set("images", images).set("shop_id", request.getShopId())
                .setSql("update_time = NOW()")
                .eq("id", blogId).eq("user_id", user.getId()).update();
        return updated ? queryBlogByid(blogId) : Result.fail("笔记更新失败");
    }

    @Override
    public Result updateVisibility(Long blogId, Integer visibility) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        if (visibility == null || (visibility != 0 && visibility != 1)) {
            return Result.fail("可见性参数无效");
        }
        Blog existing = getById(blogId);
        if (existing == null) return Result.fail("博客不存在");
        if (!user.getId().equals(existing.getUserId())) return Result.fail("无权操作该笔记");
        boolean updated = update().set("visibility", visibility).setSql("update_time = NOW()")
                .eq("id", blogId).eq("user_id", user.getId()).update();
        return updated ? queryBlogByid(blogId) : Result.fail("可见性更新失败");
    }

    @Override
    @Transactional
    public Result deleteBlog(Long blogId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        Blog existing = getById(blogId);
        if (existing == null) return Result.fail("博客不存在");
        if (!user.getId().equals(existing.getUserId())) return Result.fail("无权操作该笔记");
        blogCommentsMapper.deleteByBlogId(blogId);
        blogCollectionMapper.deleteByBlogId(blogId);
        blogLikeMapper.deleteByBlogId(blogId);
        boolean removed = removeById(blogId);
        if (!removed) throw new IllegalStateException("笔记删除失败");
        stringRedisTemplate.delete(RedisConstants.BLOG_LIKED_KEY + blogId);
        return Result.ok();
    }

    @Override
    public boolean canCurrentUserView(Blog blog) {
        if (blog == null || blog.getVisibility() == null || blog.getVisibility() == 0) return blog != null;
        UserDTO user = UserHolder.getUser();
        return user != null && user.getId().equals(blog.getUserId());
    }
  //点赞排行榜前五名
    @Override
    public Result queryBlogLikes(Long id) {
        Blog target = getById(id);
        if (target == null || !canCurrentUserView(target)) return Result.fail("笔记不存在或无权访问");
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
        if (blog == null || StrUtil.isBlank(blog.getTitle()) || StrUtil.isBlank(blog.getContent())) return Result.fail("标题和正文不能为空");
        String safeTitle = blog.getTitle().trim();
        String safeContent = blog.getContent().trim();
        if (safeTitle.length() > 255 || safeContent.length() > 2048) return Result.fail("标题或正文过长");
        if (safeTitle.indexOf('<') >= 0 || safeTitle.indexOf('>') >= 0) return Result.fail("标题只能使用纯文本");
        if (blog.getShopId() == null || shopService.getById(blog.getShopId()) == null) return Result.fail("关联商户不存在");
        String safeImages = blog.getImages() == null ? "" : blog.getImages().trim();
        if (safeImages.length() > 2048) return Result.fail("图片地址过长");
        blog.setId(null); blog.setTitle(safeTitle); blog.setContent(safeContent); blog.setImages(safeImages);
        blog.setLiked(0); blog.setComments(0); blog.setVisibility(0);
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
                .in("id", ids).eq("visibility", 0).last("ORDER BY FIELD(id," + idStr + ")").list();
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
