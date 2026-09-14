package com.smartlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.entity.BlogLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BlogLikeMapper extends BaseMapper<BlogLike> {
    @Select("SELECT COUNT(*) FROM tb_blog_like WHERE user_id = #{userId} AND blog_id = #{blogId}")
    int countByUserAndBlog(@Param("userId") Long userId, @Param("blogId") Long blogId);

    @Insert("INSERT IGNORE INTO tb_blog_like(user_id, blog_id, create_time) VALUES(#{userId}, #{blogId}, NOW())")
    int insertIgnore(@Param("userId") Long userId, @Param("blogId") Long blogId);

    @Delete("DELETE FROM tb_blog_like WHERE user_id = #{userId} AND blog_id = #{blogId}")
    int deleteByUserAndBlog(@Param("userId") Long userId, @Param("blogId") Long blogId);

    @Delete("DELETE FROM tb_blog_like WHERE blog_id = #{blogId}")
    int deleteByBlogId(@Param("blogId") Long blogId);
}
