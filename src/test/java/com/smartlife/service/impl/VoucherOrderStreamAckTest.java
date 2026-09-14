package com.smartlife.service.impl;

import com.smartlife.entity.VoucherOrder;
import com.smartlife.service.SeckillOrderPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VoucherOrderStreamAckTest {
    private VoucherOrderServiceImpl service;
    private SeckillOrderPersistenceService persistence;
    private StreamOperations<String, Object, Object> streams;
    private MapRecord<String, Object, Object> record;

    @BeforeEach @SuppressWarnings("unchecked") void setUp() {
        service = new VoucherOrderServiceImpl();
        persistence = mock(SeckillOrderPersistenceService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        streams = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class); when(lock.tryLock()).thenReturn(true); when(redisson.getLock(anyString())).thenReturn(lock);
        ReflectionTestUtils.setField(service, "seckillOrderPersistenceService", persistence);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "redissonClient", redisson);
        record = mock(MapRecord.class); when(record.getId()).thenReturn(RecordId.of("1-0"));
    }

    @Test void acknowledgesOnlyAfterPersistenceSucceeds() {
        VoucherOrder order = new VoucherOrder().setId(1L).setUserId(2L).setVoucherId(3L);
        service.persistThenAcknowledge("stream.orders", record, order);
        InOrder inOrder = inOrder(persistence, streams);
        inOrder.verify(persistence).persist(order);
        inOrder.verify(streams).acknowledge("stream.orders", "g1", RecordId.of("1-0"));
    }

    @Test void persistenceFailureDoesNotAcknowledge() {
        VoucherOrder order = new VoucherOrder().setId(1L).setUserId(2L).setVoucherId(3L);
        doThrow(new IllegalStateException("db failed")).when(persistence).persist(order);
        assertThrows(IllegalStateException.class, () -> service.persistThenAcknowledge("stream.orders", record, order));
        verify(streams, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }
}
