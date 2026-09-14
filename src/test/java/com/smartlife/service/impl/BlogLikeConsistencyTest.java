package com.smartlife.service.impl;

import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.Blog;
import com.smartlife.mapper.BlogCollectionMapper;
import com.smartlife.mapper.BlogCommentsMapper;
import com.smartlife.mapper.BlogLikeMapper;
import com.smartlife.mapper.BlogMapper;
import com.smartlife.service.IFollowService;
import com.smartlife.service.IShopService;
import com.smartlife.service.IUserService;
import com.smartlife.utils.RedisConstants;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BlogLikeConsistencyTest {

    private final BlogMapper blogMapper = mock(BlogMapper.class);
    private final BlogLikeMapper likeMapper = mock(BlogLikeMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zSet = mock(ZSetOperations.class);
    private BlogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BlogServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", blogMapper);
        ReflectionTestUtils.setField(service, "blogLikeMapper", likeMapper);
        ReflectionTestUtils.setField(service, "blogCollectionMapper", mock(BlogCollectionMapper.class));
        ReflectionTestUtils.setField(service, "blogCommentsMapper", mock(BlogCommentsMapper.class));
        ReflectionTestUtils.setField(service, "userService", mock(IUserService.class));
        ReflectionTestUtils.setField(service, "shopService", mock(IShopService.class));
        ReflectionTestUtils.setField(service, "followService", mock(IFollowService.class));
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        when(redis.opsForZSet()).thenReturn(zSet);
        when(blogMapper.selectById(9L)).thenReturn(publicBlog());
        when(blogMapper.incrementLiked(9L)).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void duplicateLikeConflictDoesNotIncrementCount() {
        login();
        when(likeMapper.countByUserAndBlog(8L, 9L)).thenReturn(0);
        when(likeMapper.insertIgnore(8L, 9L)).thenReturn(0);

        Result result = service.likeBlog(9L);

        assertTrue(result.getSuccess());
        verify(blogMapper, never()).incrementLiked(9L);
    }

    @Test
    void concurrentDuplicateLikesIncrementCountOnlyOnce() throws Exception {
        CountDownLatch bothChecked = new CountDownLatch(2);
        when(likeMapper.countByUserAndBlog(8L, 9L)).thenAnswer(invocation -> {
            bothChecked.countDown();
            assertTrue(bothChecked.await(2, TimeUnit.SECONDS));
            return 0;
        });
        AtomicInteger inserts = new AtomicInteger();
        when(likeMapper.insertIgnore(8L, 9L)).thenAnswer(invocation -> inserts.getAndIncrement() == 0 ? 1 : 0);

        runConcurrentlyTwice(() -> service.likeBlog(9L));

        verify(blogMapper, times(1)).incrementLiked(9L);
    }

    @Test
    void concurrentDuplicateCancelsDecrementCountOnlyOnce() throws Exception {
        CountDownLatch bothChecked = new CountDownLatch(2);
        when(likeMapper.countByUserAndBlog(8L, 9L)).thenAnswer(invocation -> {
            bothChecked.countDown();
            assertTrue(bothChecked.await(2, TimeUnit.SECONDS));
            return 1;
        });
        AtomicInteger deletes = new AtomicInteger();
        when(likeMapper.deleteByUserAndBlog(8L, 9L)).thenAnswer(invocation -> deletes.getAndIncrement() == 0 ? 1 : 0);

        runConcurrentlyTwice(() -> service.likeBlog(9L));

        verify(blogMapper, times(1)).decrementLikedSafely(9L);
    }

    @Test
    void repeatedCancelWithoutDeletedRelationDoesNotDecrement() {
        login();
        when(likeMapper.countByUserAndBlog(8L, 9L)).thenReturn(1);
        when(likeMapper.deleteByUserAndBlog(8L, 9L)).thenReturn(0);

        service.likeBlog(9L);

        verify(blogMapper, never()).decrementLikedSafely(9L);
    }

    @Test
    void cancelUsesNonNegativeDatabaseUpdate() {
        login();
        when(likeMapper.countByUserAndBlog(8L, 9L)).thenReturn(1);
        when(likeMapper.deleteByUserAndBlog(8L, 9L)).thenReturn(1);

        Result result = service.likeBlog(9L);

        assertTrue(result.getSuccess());
        verify(blogMapper).decrementLikedSafely(9L);
    }

    @Test
    void redisFailureDoesNotChangeSuccessfulDatabaseResult() {
        login();
        when(likeMapper.countByUserAndBlog(8L, 9L)).thenReturn(0);
        when(likeMapper.insertIgnore(8L, 9L)).thenReturn(1);
        when(zSet.add(eq(RedisConstants.BLOG_LIKED_KEY + 9L), eq("8"), anyDouble()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        Result result = service.likeBlog(9L);

        assertTrue(result.getSuccess());
        verify(likeMapper).insertIgnore(8L, 9L);
        verify(blogMapper).incrementLiked(9L);
    }

    private void runConcurrentlyTwice(java.util.concurrent.Callable<Result> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Result> first = executor.submit(() -> runAsUser(start, action));
            Future<Result> second = executor.submit(() -> runAsUser(start, action));
            start.countDown();
            assertTrue(first.get(3, TimeUnit.SECONDS).getSuccess());
            assertTrue(second.get(3, TimeUnit.SECONDS).getSuccess());
        } finally {
            executor.shutdownNow();
        }
    }

    private Result runAsUser(CountDownLatch start, java.util.concurrent.Callable<Result> action) throws Exception {
        start.await();
        login();
        try {
            return action.call();
        } finally {
            UserHolder.removeUser();
        }
    }

    private void login() {
        UserDTO user = new UserDTO();
        user.setId(8L);
        UserHolder.saveUser(user);
    }

    private Blog publicBlog() {
        return new Blog().setId(9L).setUserId(7L).setLiked(0).setVisibility(0)
                .setCreateTime(LocalDateTime.now());
    }
}
