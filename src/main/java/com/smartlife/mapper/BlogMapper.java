package com.smartlife.mapper;

import com.smartlife.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogMapper extends BaseMapper<Blog> {

    @Update("UPDATE tb_blog SET liked = COALESCE(liked, 0) + 1 WHERE id = #{blogId}")
    int incrementLiked(@Param("blogId") Long blogId);

    @Update("UPDATE tb_blog SET liked = GREATEST(COALESCE(liked, 0) - 1, 0) WHERE id = #{blogId}")
    int decrementLikedSafely(@Param("blogId") Long blogId);
}
