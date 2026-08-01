package com.smartlife.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    //进入Controller层之前要进行校验
    //前置拦截器
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截（ThreadLocal中是否有用户）
        if(UserHolder.getUser()==null){
            //没有 需要拦截并设置状态码
            response.setStatus(401);
            //拦截
            return false;
        }
        //有用户则放行
        return true;
    }

    //任务执行完之后释放资源 避免内存泄漏
    //渲染完后的
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
     //移除用户
        UserHolder.removeUser();
    }
}
