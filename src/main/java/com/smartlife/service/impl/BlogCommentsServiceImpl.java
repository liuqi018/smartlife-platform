package com.smartlife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.dto.BlogCommentCreateDTO;
import com.smartlife.dto.BlogCommentDTO;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.BlogComments;
import com.smartlife.entity.Blog;
import com.smartlife.mapper.BlogCommentsMapper;
import com.smartlife.service.IBlogCommentsService;
import com.smartlife.service.IBlogService;
import com.smartlife.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    private static final int MAX_COMMENT_LENGTH = 255;

    @Resource
    private IBlogService blogService;

    @Override
    public Result queryBlogComments(Long blogId, Integer current, Integer size) {
        Blog blog = blogService.getById(blogId);
        if (blog == null || !blogService.canCurrentUserView(blog)) {
            return Result.fail("帖子不存在");
        }
        if (current == null || current < 1 || size == null || size < 1 || size > 50) {
            return Result.fail("分页参数不合法");
        }
        long total = baseMapper.countTopLevelComments(blogId);
        long offset = (long) (current - 1) * size;
        List<BlogCommentDTO> comments = baseMapper.selectTopLevelComments(blogId, offset, size);
        return Result.ok(comments, total);
    }

    @Override
    @Transactional
    public Result createComment(Long blogId, BlogCommentCreateDTO request) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Blog blog = blogService.getById(blogId);
        if (blog == null || !blogService.canCurrentUserView(blog)) {
            return Result.fail("帖子不存在");
        }
        String content = request == null || request.getContent() == null
                ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            return Result.fail("评论内容不能超过255字");
        }

        LocalDateTime now = LocalDateTime.now();
        BlogComments comment = new BlogComments()
                .setBlogId(blogId)
                .setUserId(UserHolder.getUser().getId())
                .setParentId(0L)
                .setAnswerId(0L)
                .setContent(content)
                .setLiked(0)
                .setStatus(false)
                .setCreateTime(now)
                .setUpdateTime(now);
        if (!save(comment)) {
            throw new IllegalStateException("评论保存失败");
        }
        if (baseMapper.incrementBlogCommentCount(blogId) != 1) {
            throw new IllegalStateException("评论数量更新失败");
        }
        BlogCommentDTO result = baseMapper.selectCommentDto(comment.getId());
        if (result == null) {
            throw new IllegalStateException("评论信息查询失败");
        }
        return Result.ok(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteComment(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        if (!currentUser.getId().equals(comment.getUserId())) {
            return Result.fail("无权删除该评论");
        }

        int deleted = baseMapper.deleteByIdAndUser(id, currentUser.getId());
        if (deleted == 0) {
            return Result.ok();
        }
        if (baseMapper.decrementBlogCommentCountSafely(comment.getBlogId()) != 1) {
            throw new IllegalStateException("评论数更新失败");
        }
        return Result.ok();
    }
}
