package com.smartlife.service.impl;

import com.smartlife.dto.BlogCommentCreateDTO;
import com.smartlife.dto.BlogCommentDTO;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.Blog;
import com.smartlife.entity.BlogComments;
import com.smartlife.mapper.BlogCommentsMapper;
import com.smartlife.service.IBlogService;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BlogCommentsServiceTest {
    private final BlogCommentsMapper mapper = mock(BlogCommentsMapper.class);
    private final IBlogService blogService = mock(IBlogService.class);
    private BlogCommentsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BlogCommentsServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "blogService", blogService);
        when(blogService.canCurrentUserView(any())).thenReturn(true);
        UserDTO user = new UserDTO();
        user.setId(42L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void queriesCommentsForExistingBlogWithAuthorData() {
        BlogCommentDTO comment = commentDto();
        when(blogService.getById(8L)).thenReturn(new Blog().setId(8L));
        when(mapper.countTopLevelComments(8L)).thenReturn(1L);
        when(mapper.selectTopLevelComments(8L, 0L, 10L)).thenReturn(Collections.singletonList(comment));

        Result result = service.queryBlogComments(8L, 1, 10);

        BlogCommentDTO returned = (BlogCommentDTO) ((java.util.List<?>) result.getData()).get(0);
        assertTrue(result.getSuccess());
        assertEquals("真实用户", returned.getNickName());
        assertEquals("/imgs/user.png", returned.getIcon());
    }

    @Test
    void blogWithoutCommentsReturnsEmptyPage() {
        when(blogService.getById(8L)).thenReturn(new Blog().setId(8L));
        when(mapper.selectTopLevelComments(8L, 0L, 10L)).thenReturn(Collections.emptyList());

        Result result = service.queryBlogComments(8L, 1, 10);

        assertEquals(0L, result.getTotal());
        assertTrue(((java.util.List<?>) result.getData()).isEmpty());
    }

    @Test
    void commentListSupportsPagination() {
        when(blogService.getById(8L)).thenReturn(new Blog().setId(8L));
        when(mapper.selectTopLevelComments(8L, 20L, 10L)).thenReturn(Arrays.asList(commentDto()));

        service.queryBlogComments(8L, 3, 10);

        verify(mapper).selectTopLevelComments(8L, 20L, 10L);
    }

    @Test
    void unauthenticatedUserCannotComment() {
        UserHolder.removeUser();
        Result result = service.createComment(8L, request("hello"));
        assertFalse(result.getSuccess());
        verify(mapper, never()).insert(any());
    }

    @Test
    void blankCommentIsRejected() {
        when(blogService.getById(8L)).thenReturn(new Blog().setId(8L));
        assertFalse(service.createComment(8L, request("   ")).getSuccess());
        verify(mapper, never()).insert(any());
    }

    @Test
    void overlongCommentIsRejected() {
        when(blogService.getById(8L)).thenReturn(new Blog().setId(8L));
        char[] chars = new char[256];
        Arrays.fill(chars, 'a');
        assertFalse(service.createComment(8L, request(new String(chars))).getSuccess());
        verify(mapper, never()).insert(any());
    }

    @Test
    void nonexistentBlogCannotBeCommented() {
        when(blogService.getById(999L)).thenReturn(null);
        assertFalse(service.createComment(999L, request("hello")).getSuccess());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createsTopLevelCommentSuccessfully() {
        stubSuccessfulCreation();
        Result result = service.createComment(8L, request("  good shop  "));
        assertTrue(result.getSuccess());
        ArgumentCaptor<BlogComments> captor = ArgumentCaptor.forClass(BlogComments.class);
        verify(mapper).insert(captor.capture());
        assertAll(
                () -> assertEquals("good shop", captor.getValue().getContent()),
                () -> assertEquals(0L, captor.getValue().getParentId()),
                () -> assertEquals(0L, captor.getValue().getAnswerId()),
                () -> assertFalse(captor.getValue().getStatus())
        );
    }

    @Test
    void commentUserIdAlwaysComesFromUserHolder() {
        stubSuccessfulCreation();
        service.createComment(8L, request("hello"));
        ArgumentCaptor<BlogComments> captor = ArgumentCaptor.forClass(BlogComments.class);
        verify(mapper).insert(captor.capture());
        assertEquals(42L, captor.getValue().getUserId());
    }

    @Test
    void newCommentAtomicallyIncrementsBlogCounter() {
        stubSuccessfulCreation();
        service.createComment(8L, request("hello"));
        verify(mapper).incrementBlogCommentCount(8L);
    }

    @Test
    void createdCommentReturnsNicknameAndIcon() {
        stubSuccessfulCreation();
        BlogCommentDTO result = (BlogCommentDTO) service.createComment(8L, request("hello")).getData();
        assertEquals("真实用户", result.getNickName());
        assertEquals("/imgs/user.png", result.getIcon());
    }

    @Test
    void creatingCommentDoesNotInvokeBlogLikeLogic() {
        stubSuccessfulCreation();
        service.createComment(8L, request("hello"));
        verify(blogService, never()).likeBlog(anyLong());
    }

    @Test
    void commentAuthorCanDeleteAndDecrementsCountOnce() {
        BlogComments comment = comment(42L);
        when(mapper.selectById(101L)).thenReturn(comment);
        when(mapper.deleteByIdAndUser(101L, 42L)).thenReturn(1);
        when(mapper.decrementBlogCommentCountSafely(8L)).thenReturn(1);

        Result result = service.deleteComment(101L);

        assertTrue(result.getSuccess());
        verify(mapper).deleteByIdAndUser(101L, 42L);
        verify(mapper).decrementBlogCommentCountSafely(8L);
    }

    @Test
    void nonAuthorCannotDeleteComment() {
        when(mapper.selectById(101L)).thenReturn(comment(7L));

        Result result = service.deleteComment(101L);

        assertFalse(result.getSuccess());
        verify(mapper, never()).deleteByIdAndUser(anyLong(), anyLong());
        verify(mapper, never()).decrementBlogCommentCountSafely(anyLong());
    }

    @Test
    void nonexistentCommentIsHandledWithoutCounterChange() {
        when(mapper.selectById(101L)).thenReturn(null);

        Result result = service.deleteComment(101L);

        assertFalse(result.getSuccess());
        verify(mapper, never()).deleteByIdAndUser(anyLong(), anyLong());
        verify(mapper, never()).decrementBlogCommentCountSafely(anyLong());
    }

    @Test
    void repeatedDeleteOnlyDecrementsOnce() {
        when(mapper.selectById(101L)).thenReturn(comment(42L), null);
        when(mapper.deleteByIdAndUser(101L, 42L)).thenReturn(1);
        when(mapper.decrementBlogCommentCountSafely(8L)).thenReturn(1);

        assertTrue(service.deleteComment(101L).getSuccess());
        assertFalse(service.deleteComment(101L).getSuccess());

        verify(mapper, times(1)).deleteByIdAndUser(101L, 42L);
        verify(mapper, times(1)).decrementBlogCommentCountSafely(8L);
    }

    @Test
    void concurrentDeleteOnlyDecrementsOnce() throws Exception {
        when(mapper.selectById(101L)).thenReturn(comment(42L));
        CountDownLatch bothDeleting = new CountDownLatch(2);
        AtomicInteger deletes = new AtomicInteger();
        when(mapper.deleteByIdAndUser(101L, 42L)).thenAnswer(invocation -> {
            bothDeleting.countDown();
            assertTrue(bothDeleting.await(2, TimeUnit.SECONDS));
            return deletes.getAndIncrement() == 0 ? 1 : 0;
        });
        when(mapper.decrementBlogCommentCountSafely(8L)).thenReturn(1);

        runDeleteConcurrently();

        verify(mapper, times(1)).decrementBlogCommentCountSafely(8L);
    }

    @Test
    void failedDeleteDoesNotChangeCommentCount() {
        when(mapper.selectById(101L)).thenReturn(comment(42L));
        when(mapper.deleteByIdAndUser(101L, 42L)).thenReturn(0);

        assertTrue(service.deleteComment(101L).getSuccess());

        verify(mapper, never()).decrementBlogCommentCountSafely(anyLong());
    }

    @Test
    void decrementSqlPreventsNegativeCommentCount() throws Exception {
        org.apache.ibatis.annotations.Update update = BlogCommentsMapper.class
                .getMethod("decrementBlogCommentCountSafely", Long.class)
                .getAnnotation(org.apache.ibatis.annotations.Update.class);
        String sql = String.join(" ", update.value()).toUpperCase();
        assertTrue(sql.contains("GREATEST(COALESCE(COMMENTS, 0) - 1, 0)"));
    }

    private void stubSuccessfulCreation() {
        when(blogService.getById(8L)).thenReturn(new Blog().setId(8L));
        when(mapper.insert(any(BlogComments.class))).thenAnswer(invocation -> {
            ((BlogComments) invocation.getArgument(0)).setId(101L);
            return 1;
        });
        when(mapper.incrementBlogCommentCount(8L)).thenReturn(1);
        when(mapper.selectCommentDto(101L)).thenReturn(commentDto());
    }

    private void runDeleteConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Result> first = executor.submit(() -> deleteAsCurrentUser(start));
            Future<Result> second = executor.submit(() -> deleteAsCurrentUser(start));
            start.countDown();
            assertTrue(first.get(3, TimeUnit.SECONDS).getSuccess());
            assertTrue(second.get(3, TimeUnit.SECONDS).getSuccess());
        } finally {
            executor.shutdownNow();
        }
    }

    private Result deleteAsCurrentUser(CountDownLatch start) throws Exception {
        start.await();
        UserDTO user = new UserDTO();
        user.setId(42L);
        UserHolder.saveUser(user);
        try {
            return service.deleteComment(101L);
        } finally {
            UserHolder.removeUser();
        }
    }

    private BlogComments comment(Long userId) {
        return new BlogComments().setId(101L).setBlogId(8L).setUserId(userId);
    }

    private BlogCommentCreateDTO request(String content) {
        BlogCommentCreateDTO request = new BlogCommentCreateDTO();
        request.setContent(content);
        return request;
    }

    private BlogCommentDTO commentDto() {
        BlogCommentDTO dto = new BlogCommentDTO();
        dto.setId(101L);
        dto.setBlogId(8L);
        dto.setUserId(42L);
        dto.setNickName("真实用户");
        dto.setIcon("/imgs/user.png");
        dto.setContent("hello");
        dto.setLiked(0);
        return dto;
    }
}
