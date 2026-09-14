package com.smartlife.service;

import com.smartlife.dto.Result;
import com.smartlife.entity.Blog;
import com.smartlife.dto.BlogUpdateDTO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogByid(Long id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);

    Result queryMyBlogs(Integer current);

    Result collectBlog(Long blogId);

    Result updateBlog(Long blogId, BlogUpdateDTO request);

    Result updateVisibility(Long blogId, Integer visibility);

    Result deleteBlog(Long blogId);

    boolean canCurrentUserView(Blog blog);
}
