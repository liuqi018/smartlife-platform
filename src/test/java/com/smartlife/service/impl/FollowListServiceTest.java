package com.smartlife.service.impl;

import com.smartlife.dto.FollowUserDTO;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.mapper.FollowMapper;
import com.smartlife.service.IUserService;
import com.smartlife.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FollowListServiceTest {

    private FollowServiceImpl service;
    private FollowMapper mapper;
    private IUserService userService;

    @BeforeEach
    void setUp() {
        service = new FollowServiceImpl();
        mapper = mock(FollowMapper.class);
        userService = mock(IUserService.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", mock(StringRedisTemplate.class));
        UserDTO current = new UserDTO();
        current.setId(1020L);
        UserHolder.saveUser(current);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void listsCurrentUsersFollowsInMapperOrder() {
        when(mapper.selectFollowUsers(1020L)).thenReturn(Arrays.asList(user("1017"), user("1016")));

        Result result = service.listMyFollows();

        assertTrue(result.getSuccess());
        List<?> users = (List<?>) result.getData();
        assertEquals(2, users.size());
        assertEquals("1017", ((FollowUserDTO) users.get(0)).getId());
        verify(mapper).selectFollowUsers(1020L);
    }

    @Test
    void unauthenticatedUserCannotListFollows() {
        UserHolder.removeUser();
        assertFalse(service.listMyFollows().getSuccess());
        verifyNoInteractions(mapper);
    }

    @Test
    void cannotFollowSelf() {
        when(userService.getById(1020L)).thenReturn(new User().setId(1020L));
        assertFalse(service.follow(1020L, true).getSuccess());
        verify(mapper, never()).insert(any());
    }

    private FollowUserDTO user(String id) {
        FollowUserDTO user = new FollowUserDTO();
        user.setId(id);
        user.setNickName("用户" + id);
        user.setIcon("/imgs/icons/default-icon.png");
        user.setCity("南京");
        return user;
    }
}
