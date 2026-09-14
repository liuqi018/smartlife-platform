package com.smartlife.controller;


import cn.hutool.core.bean.BeanUtil;
import com.smartlife.dto.LoginFormDTO;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.dto.NicknameUpdateDTO;
import com.smartlife.dto.IntroduceUpdateDTO;
import com.smartlife.dto.GenderUpdateDTO;
import com.smartlife.dto.CityUpdateDTO;
import com.smartlife.dto.BirthdayUpdateDTO;
import com.smartlife.dto.IconUpdateDTO;
import com.smartlife.entity.User;
import com.smartlife.entity.UserInfo;
import com.smartlife.service.IUserInfoService;
import com.smartlife.service.IUserService;
import com.smartlife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone,session);
    }
    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token){
        return userService.logout(token);
    }

    @GetMapping("/me")
    public Result me(){
        // 拦截器中已经把user放在userHolder中了
        //获取当前登录的用户并返回
        UserDTO user= UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/me/profile")
    public Result myProfile() {
        return userService.queryMyProfile();
    }

    @PutMapping("/me/nickname")
    public Result updateNickname(@RequestBody NicknameUpdateDTO request,
                                 @RequestHeader(value = "authorization", required = false) String token) {
        return userService.updateNickname(request == null ? null : request.getNickName(), token);
    }

    @PutMapping("/me/introduce")
    public Result updateIntroduce(@RequestBody IntroduceUpdateDTO request) {
        return userService.updateIntroduce(request == null ? null : request.getIntroduce());
    }

    @PutMapping("/me/gender")
    public Result updateGender(@RequestBody GenderUpdateDTO request) {
        return userService.updateGender(request == null ? null : request.getGender());
    }

    @PutMapping("/me/city")
    public Result updateCity(@RequestBody CityUpdateDTO request) {
        return userService.updateCity(request == null ? null : request.getCity());
    }

    @PutMapping("/me/birthday")
    public Result updateBirthday(@RequestBody BirthdayUpdateDTO request) {
        return userService.updateBirthday(request == null ? null : request.getBirthday());
    }

    @PutMapping("/me/icon")
    public Result updateIcon(@RequestBody IconUpdateDTO request,
                             @RequestHeader(value = "authorization", required = false) String token) {
        return userService.updateIcon(request == null ? null : request.getIcon(), token);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
    //根据id查询用户

// UserController 根据id查询用户
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }
    //实现签到功能的接口
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }
    //签到统计
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
