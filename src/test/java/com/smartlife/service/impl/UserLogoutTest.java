package com.smartlife.service.impl;

import com.smartlife.dto.UserDTO;
import com.smartlife.utils.RedisConstants;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class UserLogoutTest {
    @Test void logoutDeletesTokenAndClearsThreadLocal() {
        UserServiceImpl service = new UserServiceImpl();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        UserDTO user = new UserDTO(); user.setId(7L); UserHolder.saveUser(user);
        service.logout("token-1");
        verify(redis).delete(RedisConstants.LOGIN_USER_KEY + "token-1");
        assertNull(UserHolder.getUser());
    }
}
