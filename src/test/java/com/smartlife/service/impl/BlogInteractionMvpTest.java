package com.smartlife.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlife.dto.BlogUpdateDTO;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.entity.Blog;
import com.smartlife.entity.Shop;
import com.smartlife.entity.User;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlogInteractionMvpTest {
    private final BlogMapper blogMapper = mock(BlogMapper.class);
    private final BlogCollectionMapper collectionMapper = mock(BlogCollectionMapper.class);
    private final BlogCommentsMapper commentsMapper = mock(BlogCommentsMapper.class);
    private final BlogLikeMapper likeMapper = mock(BlogLikeMapper.class);
    private final IUserService userService = mock(IUserService.class);
    private final IShopService shopService = mock(IShopService.class);
    private final IFollowService followService = mock(IFollowService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zSet = mock(ZSetOperations.class);
    private BlogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BlogServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", blogMapper);
        ReflectionTestUtils.setField(service, "blogCollectionMapper", collectionMapper);
        ReflectionTestUtils.setField(service, "blogCommentsMapper", commentsMapper);
        ReflectionTestUtils.setField(service, "blogLikeMapper", likeMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "shopService", shopService);
        ReflectionTestUtils.setField(service, "followService", followService);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        when(redis.opsForZSet()).thenReturn(zSet);
        when(userService.getById(anyLong())).thenReturn(new User().setId(7L).setNickName("author").setIcon("icon"));
    }

    @AfterEach
    void cleanUser() { UserHolder.removeUser(); }

    @Test
    void ownerCanEdit() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(shopService.getById(3L)).thenReturn(new Shop().setId(3L));
        when(blogMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        Result result = service.updateBlog(9L, update());
        assertTrue(result.getSuccess());
    }

    @Test
    void nonOwnerCannotEdit() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        assertFalse(service.updateBlog(9L, update()).getSuccess());
        verify(blogMapper, never()).update(any(), any());
    }

    @Test
    void ownerCanDeleteAndRelationsAreRemoved() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(blogMapper.deleteById(any(Serializable.class))).thenReturn(1);
        assertTrue(service.deleteBlog(9L).getSuccess());
        verify(commentsMapper).deleteByBlogId(9L);
        verify(collectionMapper).deleteByBlogId(9L);
        verify(redis).delete(RedisConstants.BLOG_LIKED_KEY + 9L);
    }

    @Test
    void nonOwnerCannotDelete() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        assertFalse(service.deleteBlog(9L).getSuccess());
        verify(blogMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void ownerCanChangeVisibility() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0), blog(7L, 1));
        when(blogMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertTrue(service.updateVisibility(9L, 1).getSuccess());
    }

    @Test
    void ownerCanChangePrivateBlogToPublicAndZeroIsNotTreatedAsNull() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 1), blog(7L, 0));
        when(blogMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        Result result = service.updateVisibility(9L, Integer.valueOf(0));
        assertTrue(result.getSuccess());
        verify(blogMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void invalidVisibilityIsRejected() {
        login(7L);
        assertFalse(service.updateVisibility(9L, 2).getSuccess());
        verify(blogMapper, never()).update(any(), any());
    }

    @Test
    void nonOwnerCannotChangeVisibility() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        assertFalse(service.updateVisibility(9L, 1).getSuccess());
    }

    @Test
    void privateBlogIsVisibleToOwner() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 1));
        assertTrue(service.queryBlogByid(9L).getSuccess());
    }

    @Test
    void privateBlogIsHiddenFromOtherUser() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 1));
        assertFalse(service.queryBlogByid(9L).getSuccess());
    }

    @Test
    void publicBlogIsVisibleToOtherUser() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        assertTrue(service.queryBlogByid(9L).getSuccess());
    }

    @Test
    void collectCreatesPersistentRelation() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(collectionMapper.insertIgnore(8L, 9L)).thenReturn(1);
        Result result = service.collectBlog(9L);
        assertEquals(Boolean.TRUE, result.getData());
    }

    @Test
    void duplicateCollectionCannotCreateDuplicateRow() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(collectionMapper.insertIgnore(8L, 9L)).thenReturn(0);
        assertTrue(service.collectBlog(9L).getSuccess());
        verify(collectionMapper, times(1)).insertIgnore(8L, 9L);
    }

    @Test
    void collectAgainCancelsCollection() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(collectionMapper.countByUserAndBlog(8L, 9L)).thenReturn(1);
        Result result = service.collectBlog(9L);
        assertEquals(Boolean.FALSE, result.getData());
        verify(collectionMapper).deleteByUserAndBlog(8L, 9L);
    }

    @Test
    void detailReturnsCollectionLikeAndOwnerState() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(zSet.score(RedisConstants.BLOG_LIKED_KEY + 9L, "7")).thenReturn(1D);
        when(collectionMapper.countByUserAndBlog(7L, 9L)).thenReturn(1);
        Blog result = (Blog) service.queryBlogByid(9L).getData();
        assertTrue(result.getLikedByCurrentUser());
        assertTrue(result.getCollectedByCurrentUser());
        assertTrue(result.getIsOwner());
    }

    @Test
    void existingLikeToggleStillWorks() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(zSet.score(RedisConstants.BLOG_LIKED_KEY + 9L, "8")).thenReturn(null);
        when(blogMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertTrue(service.likeBlog(9L).getSuccess());
        verify(zSet).add(eq(RedisConstants.BLOG_LIKED_KEY + 9L), eq("8"), anyDouble());
    }

    @Test
    void otherUserCannotLikePrivateBlog() {
        login(8L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 1));
        assertFalse(service.likeBlog(9L).getSuccess());
        verify(zSet, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void ownerCanLikePrivateBlog() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 1));
        when(blogMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertTrue(service.likeBlog(9L).getSuccess());
    }

    @Test
    void commentRelationsRemainManagedByCommentMapper() {
        login(7L); when(blogMapper.selectById(9L)).thenReturn(blog(7L, 0));
        when(blogMapper.deleteById(any(Serializable.class))).thenReturn(1);
        service.deleteBlog(9L);
        verify(commentsMapper).deleteByBlogId(9L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void myBlogsAreNewestFirst() {
        login(7L);
        when(blogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<Blog> page = invocation.getArgument(0);
            page.setRecords(Arrays.asList(new Blog().setId(52L), new Blog().setId(51L)));
            return page;
        });
        Result result = service.queryMyBlogs(1);
        assertEquals(52L, ((Blog) ((java.util.List<?>) result.getData()).get(0)).getId());
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(blogMapper).selectPage(any(Page.class), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("order by create_time desc,id desc"));
    }

    private void login(long id) { UserDTO user = new UserDTO(); user.setId(id); UserHolder.saveUser(user); }
    private Blog blog(long owner, int visibility) {
        return new Blog().setId(9L).setUserId(owner).setShopId(3L).setTitle("title")
                .setContent("content").setImages("image").setLiked(0).setComments(0)
                .setVisibility(visibility).setCreateTime(LocalDateTime.now());
    }
    private BlogUpdateDTO update() {
        BlogUpdateDTO dto = new BlogUpdateDTO(); dto.setTitle(" new title ");
        dto.setContent(" new content "); dto.setImages("image"); dto.setShopId(3L); return dto;
    }
}
