package com.smartlife.mapper;

import com.smartlife.entity.BlogComments;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.dto.BlogCommentDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogCommentsMapper extends BaseMapper<BlogComments> {

    @Select("SELECT c.id, c.blog_id, c.user_id, u.nick_name, u.icon, c.content, " +
            "COALESCE(c.liked, 0) AS liked, c.create_time " +
            "FROM tb_blog_comments c JOIN tb_user u ON u.id = c.user_id " +
            "WHERE c.blog_id = #{blogId} AND c.status = 0 AND c.parent_id = 0 " +
            "ORDER BY c.create_time DESC, c.id DESC LIMIT #{offset}, #{size}")
    List<BlogCommentDTO> selectTopLevelComments(@Param("blogId") Long blogId,
                                                 @Param("offset") long offset,
                                                 @Param("size") long size);

    @Select("SELECT COUNT(*) FROM tb_blog_comments " +
            "WHERE blog_id = #{blogId} AND status = 0 AND parent_id = 0")
    long countTopLevelComments(@Param("blogId") Long blogId);

    @Update("UPDATE tb_blog SET comments = COALESCE(comments, 0) + 1 WHERE id = #{blogId}")
    int incrementBlogCommentCount(@Param("blogId") Long blogId);

    @Select("SELECT c.id, c.blog_id, c.user_id, u.nick_name, u.icon, c.content, " +
            "COALESCE(c.liked, 0) AS liked, c.create_time " +
            "FROM tb_blog_comments c JOIN tb_user u ON u.id = c.user_id " +
            "WHERE c.id = #{commentId} AND c.status = 0")
    BlogCommentDTO selectCommentDto(@Param("commentId") Long commentId);

    @Delete("DELETE FROM tb_blog_comments WHERE blog_id = #{blogId}")
    int deleteByBlogId(@Param("blogId") Long blogId);

    @Delete("DELETE FROM tb_blog_comments WHERE id = #{commentId} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Update("UPDATE tb_blog SET comments = GREATEST(COALESCE(comments, 0) - 1, 0) " +
            "WHERE id = #{blogId}")
    int decrementBlogCommentCountSafely(@Param("blogId") Long blogId);

}
