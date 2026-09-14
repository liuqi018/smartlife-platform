package com.smartlife.mapper;

import com.smartlife.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.dto.FollowUserDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {

    @Select("SELECT CAST(u.id AS CHAR) AS id, u.nick_name AS nickName, u.icon, ui.city " +
            "FROM tb_follow f " +
            "INNER JOIN tb_user u ON u.id = f.follow_user_id " +
            "LEFT JOIN tb_user_info ui ON ui.user_id = u.id " +
            "WHERE f.user_id = #{userId} " +
            "ORDER BY f.create_time DESC, f.id DESC")
    List<FollowUserDTO> selectFollowUsers(@Param("userId") Long userId);
}
