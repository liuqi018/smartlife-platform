package com.smartlife.controller;


import com.smartlife.dto.Result;
import com.smartlife.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    @GetMapping("/of/blog/{blogId}")
    public Result queryBlogComments(@PathVariable Long blogId,
                                    @RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "10") Integer size) {
        return blogCommentsService.queryBlogComments(blogId, current, size);
    }

    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable Long id) {
        return blogCommentsService.deleteComment(id);
    }

}
