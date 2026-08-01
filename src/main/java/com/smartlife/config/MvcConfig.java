package com.smartlife.config;

import com.smartlife.utils.LoginInterceptor;
import com.smartlife.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**","/api/health/**","/test/**")
                .order(0);
        registry.addInterceptor(
                new LoginInterceptor()
        ).excludePathPatterns(
                "/voucher/**","/upload/**","/shop/**",
                "/shop-type/**",
                "/blog/hot","/user/code","/user/login",
                "/actuator/**","/api/health/**","/test/**"
        ).order(1);
    }
}



