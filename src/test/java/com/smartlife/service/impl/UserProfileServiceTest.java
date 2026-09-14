package com.smartlife.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.dto.UserProfileDTO;
import com.smartlife.entity.User;
import com.smartlife.entity.UserInfo;
import com.smartlife.mapper.UserInfoMapper;
import com.smartlife.mapper.UserMapper;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private UserServiceImpl service;
    private UserMapper userMapper;
    private UserInfoMapper userInfoMapper;
    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new UserServiceImpl();
        userMapper = mock(UserMapper.class);
        userInfoMapper = mock(UserInfoMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        ReflectionTestUtils.setField(service, "userInfoMapper", userInfoMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        login(1020L);
        when(userMapper.selectById(1020L)).thenReturn(user());
        when(userInfoMapper.selectById(1020L)).thenReturn(info());
        when(userMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(userInfoMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void queryMyProfileReturnsCurrentUserAndReadOnlyBenefits() {
        Result result = service.queryMyProfile();
        assertTrue(result.getSuccess());
        UserProfileDTO profile = (UserProfileDTO) result.getData();
        assertEquals("1020", profile.getId());
        assertEquals("南京", profile.getCity());
        assertEquals(88, profile.getPoints());
        assertEquals(2, profile.getLevel());
    }

    @Test
    void updateNicknameSucceedsAndRefreshesLoginCache() {
        Result result = service.updateNickname("  新昵称  ", "token-1");
        assertTrue(result.getSuccess());
        verify(userMapper).update(isNull(), any(Wrapper.class));
        verify(hashOperations).put("login:token:token-1", "nickName", "新昵称");
    }

    @Test
    void blankNicknameFails() {
        assertFalse(service.updateNickname("   ", "token-1").getSuccess());
        verify(userMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void updateIntroduceSucceeds() {
        assertTrue(service.updateIntroduce("  简单介绍  ").getSuccess());
        verify(userInfoMapper).ensureExists(1020L);
    }

    @Test
    void updateGenderSucceeds() {
        assertTrue(service.updateGender(2).getSuccess());
    }

    @Test
    void invalidGenderFails() {
        assertFalse(service.updateGender(3).getSuccess());
        verify(userInfoMapper, never()).ensureExists(anyLong());
    }

    @Test
    void updateCitySucceeds() {
        assertTrue(service.updateCity("  杭州  ").getSuccess());
    }

    @Test
    void updateBirthdaySucceeds() {
        assertTrue(service.updateBirthday("2000-01-01").getSuccess());
    }

    @Test
    void futureBirthdayFails() {
        assertFalse(service.updateBirthday(LocalDate.now().plusDays(1).toString()).getSuccess());
        verify(userInfoMapper, never()).ensureExists(anyLong());
    }

    @Test
    void updateIconSucceedsAndRefreshesLoginCache() {
        Result result = service.updateIcon(" /imgs/blogs/avatar.jpg ", "token-2");
        assertTrue(result.getSuccess());
        verify(hashOperations).put("login:token:token-2", "icon", "/imgs/blogs/avatar.jpg");
    }

    @Test
    void unauthenticatedUserCannotModifyProfile() {
        UserHolder.removeUser();
        assertFalse(service.updateCity("南京").getSuccess());
        verify(userInfoMapper, never()).ensureExists(anyLong());
    }

    private void login(long id) {
        UserDTO current = new UserDTO();
        current.setId(id);
        current.setNickName("旧昵称");
        UserHolder.saveUser(current);
    }

    private User user() {
        User user = new User();
        user.setId(1020L);
        user.setNickName("数据库昵称");
        user.setIcon("/imgs/icons/default-icon.png");
        return user;
    }

    private UserInfo info() {
        UserInfo info = new UserInfo();
        info.setUserId(1020L);
        info.setIntroduce("介绍");
        info.setGender(1);
        info.setCity("南京");
        info.setBirthday(LocalDate.of(2000, 1, 1));
        info.setCredits(88);
        info.setLevel(2);
        return info;
    }
}
