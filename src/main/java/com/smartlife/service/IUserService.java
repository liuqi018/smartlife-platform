package com.smartlife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.dto.LoginFormDTO;
import com.smartlife.dto.Result;
import com.smartlife.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
    Result logout(String token);

    Result sign();

    Result signCount();

    Result queryMyProfile();
    Result updateNickname(String nickname, String token);
    Result updateIntroduce(String introduce);
    Result updateGender(Integer gender);
    Result updateCity(String city);
    Result updateBirthday(String birthday);
    Result updateIcon(String icon, String token);
}
