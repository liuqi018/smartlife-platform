package com.smartlife.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlife.dto.Result;
import com.smartlife.dto.BlogCommentCreateDTO;
import com.smartlife.dto.BlogUpdateDTO;
import com.smartlife.dto.BlogVisibilityDTO;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.Blog;
import com.smartlife.service.IBlogService;
import com.smartlife.service.IBlogCommentsService;
import com.smartlife.service.IUserService;
import com.smartlife.utils.SystemConstants;
import com.smartlife.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {
    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private IBlogCommentsService blogCommentsService;
    @PostMapping
    //保存发布的blog
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        return blogService.likeBlog(id);
    }
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlogs(current);
    }
    //分页查询
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
       return blogService.queryHotBlog(current);
    }
    //笔记页面展示用户信息
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id")Long id){
        return blogService.queryBlogByid(id);
    }
    @PutMapping("/{blogId}/collect")
    public Result collectBlog(@PathVariable Long blogId) {
        return blogService.collectBlog(blogId);
    }
    @PutMapping("/{blogId}")
    public Result updateBlog(@PathVariable Long blogId, @RequestBody BlogUpdateDTO request) {
        return blogService.updateBlog(blogId, request);
    }
    @PutMapping("/{blogId}/visibility")
    public Result updateVisibility(@PathVariable Long blogId, @RequestBody BlogVisibilityDTO request) {
        return blogService.updateVisibility(blogId, request == null ? null : request.getVisibility());
    }
    @DeleteMapping("/{blogId}")
    public Result deleteBlog(@PathVariable Long blogId) {
        return blogService.deleteBlog(blogId);
    }
    @PostMapping("/{blogId}/comments")
    public Result createComment(@PathVariable Long blogId,
                                @RequestBody BlogCommentCreateDTO request) {
        return blogCommentsService.createComment(blogId, request);
    }
    //点赞排行榜
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id")Long id){
        return blogService.queryBlogLikes(id);
    }
    //根据Id查询blog 分页查询
    // BlogController
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id)
                .eq("visibility", 0)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }
    @GetMapping("/of/follow")
    //ZREVRANGEBYSCORE key maxTime minTime LIMIT offset count
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,//初始的时候是0
                                    @RequestParam(value="offset",defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }
}
