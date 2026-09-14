package com.smartlife.mapper;

import com.smartlife.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    @Insert("INSERT IGNORE INTO tb_user_info(user_id, city, introduce, gender, credits, level, create_time, update_time) " +
            "VALUES(#{userId}, '', '', 0, 0, 0, NOW(), NOW())")
    int ensureExists(@Param("userId") Long userId);

}
