package com.smartlife.service;

import com.smartlife.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.dto.BlogCommentCreateDTO;
import com.smartlife.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    Result queryBlogComments(Long blogId, Integer current, Integer size);

    Result createComment(Long blogId, BlogCommentCreateDTO request);

    Result deleteComment(Long id);

}
