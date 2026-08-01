package com.smartlife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.dto.LoginFormDTO;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.User;
import com.smartlife.mapper.UserMapper;
import com.smartlife.service.IUserService;
import com.smartlife.utils.RegexUtils;
import com.smartlife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.smartlife.utils.RedisConstants.*;
import static com.smartlife.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
   //用 Redis+token 的搭配
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //实现发短信验证码功能
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号---调用工具类中的RegexUtils
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不符合就返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合就生成验证码  利用第三方的工具包  RandomUtil
        String code= RandomUtil.randomNumbers(6);//6位的验证码
        //4.将生成的验证码保存到redis中 key value 延迟时间 单位
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5.发送验证码
        log.info("发送短信验证码成功,验证码：{}",code);
        return Result.ok();
    }
    //实现短信验证码登录功能
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone=loginForm.getPhone();//获取手机号
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //不符合就返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3.从Redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code=loginForm.getCode();
       if(cacheCode==null || !cacheCode.equals(code)) {
           //3.不一致，报错
           return Result.fail("验证码输入错误！");
       }
        //4.一致，根据手机号查询用户  用Mybatis-plus直接自动查询
        User user=query().eq("phone",phone).one();
        //5.判断用户是否存在
       if(user==null) {
           //6.不存在，创建新用户并保存
           user = createUserWithPhone(phone);
       }
        //7.保存用户信息到Redis中
        //7.1 随机生成token,作为登陆令牌 利用UUID
        String token= UUID.randomUUID().toString(true);
        //7.2 将User对象转为Hash存储 所有的都得是字符串类型
        UserDTO userDTO= BeanUtil.copyProperties(user,UserDTO.class);
        Map<String,Object>usermap= BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        //7.3 存储数据到Redis
        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,usermap);
        //7.4设置token有效期 30分钟
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL,TimeUnit.MINUTES);
        //8.返回Token
        return Result.ok(token);
    }
    private User createUserWithPhone(String phone) {
       //1.创建用户
       User user=new User();
       user.setPhone(phone);
       user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
       //2.保存用户到数据库 利用mybatis-plus的功能
        save(user);
       return user;
    }
    //签到功能
    @Override
    public Result sign() {
        //1.获取当前登陆的用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now=LocalDateTime.now();
        //3.拼接key
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key= USER_SIGN_KEY+userId+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }
    //签到统计功能
    @Override
    public Result signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now=LocalDateTime.now();
        //3.拼接key
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key= USER_SIGN_KEY+userId+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月今天为止的所有签到记录，返回的是一个十进制的数字
        //BITFIELD sign:202203 GET u14 0  从第 offset=0 位开始读取 bit
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(result==null||result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        //6.循环遍历
        Long num=result.get(0);//num=5
        if(num==null||num==0){
            return Result.ok(0);
        }
        int count=0;
        while (true){
            //7.让这个数字和1做与运算，得到数字的最后一个bit位
            if((num&1)==0){
                //判断这个bit位是否为0
                break;
                //如果为0，说明未签到 结束
            }else{
                //如果不为0，说明已签到，计数器加一
                count++;
                //把数字右移一位，抛弃最后一位进行下一位的与运算
            }
            num>>>=1;//无符号右移一位
        }
        return Result.ok(count);
    }

}
