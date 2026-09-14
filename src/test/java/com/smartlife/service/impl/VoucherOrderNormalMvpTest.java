package com.smartlife.service.impl;

import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.dto.VoucherOrderDetailDTO;
import com.smartlife.entity.Voucher;
import com.smartlife.entity.VoucherOrder;
import com.smartlife.entity.SeckillVoucher;
import com.smartlife.mapper.VoucherOrderMapper;
import com.smartlife.service.ISeckillVoucherService;
import com.smartlife.service.IVoucherService;
import com.smartlife.utils.RedisIdWorker;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoucherOrderNormalMvpTest {
    private final VoucherOrderMapper mapper = mock(VoucherOrderMapper.class);
    private final IVoucherService voucherService = mock(IVoucherService.class);
    private final RedisIdWorker idWorker = mock(RedisIdWorker.class);
    private final ISeckillVoucherService seckillVoucherService = mock(ISeckillVoucherService.class);
    private VoucherOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VoucherOrderServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "voucherService", voucherService);
        ReflectionTestUtils.setField(service, "redisIdWorker", idWorker);
        ReflectionTestUtils.setField(service, "iSeckillVoucherService", seckillVoucherService);
        setCurrentUser();
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void createsNormalVoucherOrderWithPendingStatusAndStringId() {
        stubNewNormalOrder(624726825326477313L);
        Result result = service.createNormalOrder(10L);
        assertTrue(result.getSuccess());
        assertEquals("624726825326477313", result.getData());
        ArgumentCaptor<VoucherOrder> order = ArgumentCaptor.forClass(VoucherOrder.class);
        verify(mapper).insert(order.capture());
        assertAll(
                () -> assertEquals(7L, order.getValue().getUserId()),
                () -> assertEquals(10L, order.getValue().getVoucherId()),
                () -> assertEquals(1, order.getValue().getPayType()),
                () -> assertEquals(1, order.getValue().getStatus())
        );
    }

    @Test
    void rejectsSeckillVoucherOnNormalEndpoint() {
        when(voucherService.getById(10L)).thenReturn(voucher(1, 1));
        assertFalse(service.createNormalOrder(10L).getSuccess());
        verify(mapper, never()).insert(any());
    }

    @Test
    void rejectsNormalVoucherOnSeckillEndpoint() {
        when(voucherService.getById(10L)).thenReturn(voucher(0, 1));
        assertFalse(service.seckillVoucher(10L).getSuccess());
        verifyNoInteractions(seckillVoucherService);
    }

    @Test
    void rejectsOfflineVoucherOnSeckillEndpoint() {
        when(voucherService.getById(10L)).thenReturn(voucher(1, 2));
        assertFalse(service.seckillVoucher(10L).getSuccess());
        verifyNoInteractions(seckillVoucherService);
    }

    @Test
    void rejectsSeckillBeforeBeginTime() {
        when(voucherService.getById(10L)).thenReturn(voucher(1, 1));
        when(seckillVoucherService.getById(10L)).thenReturn(new SeckillVoucher()
                .setVoucherId(10L).setBeginTime(LocalDateTime.now().plusMinutes(1))
                .setEndTime(LocalDateTime.now().plusMinutes(2)));
        Result result = service.seckillVoucher(10L);
        assertFalse(result.getSuccess());
        assertEquals("秒杀尚未开始", result.getErrorMsg());
    }

    @Test
    void rejectsSeckillAfterEndTime() {
        when(voucherService.getById(10L)).thenReturn(voucher(1, 1));
        when(seckillVoucherService.getById(10L)).thenReturn(new SeckillVoucher()
                .setVoucherId(10L).setBeginTime(LocalDateTime.now().minusMinutes(2))
                .setEndTime(LocalDateTime.now().minusMinutes(1)));
        Result result = service.seckillVoucher(10L);
        assertFalse(result.getSuccess());
        assertEquals("秒杀已结束", result.getErrorMsg());
    }

    @Test
    void rejectsInactiveVoucher() {
        when(voucherService.getById(10L)).thenReturn(voucher(0, 2));
        assertFalse(service.createNormalOrder(10L).getSuccess());
        verify(mapper, never()).insert(any());
    }

    @Test
    void pendingNormalOrderIsReused() {
        when(voucherService.getById(10L)).thenReturn(voucher(0, 1));
        when(mapper.selectActiveOrderId(7L, 10L)).thenReturn(624726825326477313L);
        Result result = service.createNormalOrder(10L);
        assertEquals("624726825326477313", result.getData());
        verify(mapper, never()).insert(any(VoucherOrder.class));
    }

    @Test
    void cannotReadAnotherUsersOrder() {
        when(mapper.selectDetailForUser(99L, 7L)).thenReturn(null);
        assertFalse(service.queryOrderDetail(99L).getSuccess());
        verify(mapper).selectDetailForUser(99L, 7L);
    }

    @Test
    void orderWithinFiveMinutesCanBePaid() {
        when(mapper.selectDetailForUser(1001L, 7L))
                .thenReturn(detail(1, LocalDateTime.now().minusMinutes(1)), detail(2));
        when(mapper.markPaid(eq(1001L), eq(7L), any())).thenReturn(1);
        Result result = service.mockPay(1001L);
        assertTrue(result.getSuccess());
        assertEquals(2, ((VoucherOrderDetailDTO) result.getData()).getStatus());
    }

    @Test
    void expiredOrderCannotBePaidAndBecomesCancelled() {
        when(mapper.selectDetailForUser(1001L, 7L))
                .thenReturn(detail(1, LocalDateTime.now().minusMinutes(6)), detail(4));
        when(mapper.cancelUnpaid(1001L, 7L)).thenReturn(1);
        Result result = service.mockPay(1001L);
        assertFalse(result.getSuccess());
        assertEquals("订单已超时取消", result.getErrorMsg());
        verify(mapper).cancelUnpaid(1001L, 7L);
        verify(mapper, never()).markPaid(anyLong(), anyLong(), any());
    }

    @Test
    void detailQueryFallsBackToCancelExpiredOrder() {
        when(mapper.selectDetailForUser(1001L, 7L))
                .thenReturn(detail(1, LocalDateTime.now().minusMinutes(6)), detail(4));
        when(mapper.cancelUnpaid(1001L, 7L)).thenReturn(1);
        Result result = service.queryOrderDetail(1001L);
        assertEquals(4, ((VoucherOrderDetailDTO) result.getData()).getStatus());
    }

    @Test
    void unpaidOrderCanBeCancelled() {
        when(mapper.selectDetailForUser(1001L, 7L)).thenReturn(detail(1), detail(4));
        when(mapper.cancelUnpaid(1001L, 7L)).thenReturn(1);
        Result result = service.cancelOrder(1001L);
        assertTrue(result.getSuccess());
        assertEquals(4, ((VoucherOrderDetailDTO) result.getData()).getStatus());
    }

    @Test
    void paidOrderCannotBeCancelled() {
        when(mapper.selectDetailForUser(1001L, 7L)).thenReturn(detail(2));
        assertFalse(service.cancelOrder(1001L).getSuccess());
        verify(mapper, never()).cancelUnpaid(anyLong(), anyLong());
    }

    @Test
    void payAndCancelCannotBothWin() throws Exception {
        AtomicInteger state = new AtomicInteger(1);
        when(mapper.selectDetailForUser(1001L, 7L)).thenAnswer(invocation ->
                detail(state.get(), LocalDateTime.now().minusMinutes(1)));
        when(mapper.markPaid(eq(1001L), eq(7L), any())).thenAnswer(invocation ->
                state.compareAndSet(1, 2) ? 1 : 0);
        when(mapper.cancelUnpaid(1001L, 7L)).thenAnswer(invocation ->
                state.compareAndSet(1, 4) ? 1 : 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            final boolean pay = i == 0;
            executor.submit(() -> {
                setCurrentUser();
                try {
                    start.await();
                    Result result = pay ? service.mockPay(1001L) : service.cancelOrder(1001L);
                    if (result.getSuccess()) successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    UserHolder.removeUser();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        assertEquals(1, successes.get());
        assertTrue(state.get() == 2 || state.get() == 4);
    }

    @Test
    void paidOrderCanBeRefundedImmediately() {
        when(mapper.selectDetailForUser(1001L, 7L)).thenReturn(detail(2), detail(6));
        when(mapper.markRefunding(1001L, 7L)).thenReturn(1);
        when(mapper.markRefunded(eq(1001L), eq(7L), any())).thenReturn(1);
        Result result = service.refundOrder(1001L);
        assertTrue(result.getSuccess());
        assertEquals(6, ((VoucherOrderDetailDTO) result.getData()).getStatus());
    }

    @Test
    void usedOrderCannotBeRefunded() {
        when(mapper.selectDetailForUser(1001L, 7L)).thenReturn(detail(3));
        assertFalse(service.refundOrder(1001L).getSuccess());
        verify(mapper, never()).markRefunding(anyLong(), anyLong());
    }

    @Test
    void cancelledNormalOrderAllowsRepurchase() {
        assertNormalRepurchaseCreatesNewOrder();
    }

    @Test
    void refundedNormalOrderAllowsRepurchase() {
        assertNormalRepurchaseCreatesNewOrder();
    }

    @Test
    void cancelledSeckillOrderStillBlocksAnotherSeckillDatabaseOrder() {
        assertHistoricalSeckillOrderBlocksCreation();
    }

    @Test
    void refundedSeckillOrderStillBlocksAnotherSeckillDatabaseOrder() {
        assertHistoricalSeckillOrderBlocksCreation();
    }

    @Test
    void myOrdersAreScopedToCurrentUserAndIdsStayStrings() {
        VoucherOrderDetailDTO order = detail(1);
        order.setOrderId("624726825326477313");
        when(mapper.countUserOrders(7L, 1)).thenReturn(1L);
        when(mapper.selectUserOrders(7L, 1, 0L, 10L)).thenReturn(Collections.singletonList(order));
        Result result = service.queryMyOrders(1, 10, 1);
        assertEquals(1L, result.getTotal());
        List<?> orders = (List<?>) result.getData();
        assertEquals("624726825326477313", ((VoucherOrderDetailDTO) orders.get(0)).getOrderId());
        verify(mapper).cancelExpiredForUser(7L);
        verify(mapper).selectUserOrders(7L, 1, 0L, 10L);
    }

    @Test
    void scheduledScanCancelsExpiredOrders() {
        when(mapper.cancelAllExpiredUnpaid()).thenReturn(2);
        service.cancelExpiredOrders();
        verify(mapper).cancelAllExpiredUnpaid();
    }

    private void assertNormalRepurchaseCreatesNewOrder() {
        stubNewNormalOrder(624726825326477314L);
        Result result = service.createNormalOrder(10L);
        assertEquals("624726825326477314", result.getData());
        verify(mapper).cancelExpiredForVoucher(7L, 10L);
        verify(mapper).insert(any(VoucherOrder.class));
    }

    private void assertHistoricalSeckillOrderBlocksCreation() {
        when(mapper.selectCount(any())).thenReturn(1);
        service.createVoucherOrder(new VoucherOrder().setId(1001L).setUserId(7L).setVoucherId(10L));
        verify(mapper, never()).insert(any(VoucherOrder.class));
        verifyNoInteractions(seckillVoucherService);
    }

    private void stubNewNormalOrder(long id) {
        when(voucherService.getById(10L)).thenReturn(voucher(0, 1));
        when(mapper.selectActiveOrderId(7L, 10L)).thenReturn(null);
        when(idWorker.nextId("order")).thenReturn(id);
        when(mapper.insert(any(VoucherOrder.class))).thenReturn(1);
    }

    private void setCurrentUser() {
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
    }

    private Voucher voucher(int type, int status) {
        return new Voucher().setId(10L).setType(type).setStatus(status);
    }

    private VoucherOrderDetailDTO detail(int status) {
        return detail(status, LocalDateTime.now());
    }

    private VoucherOrderDetailDTO detail(int status, LocalDateTime createTime) {
        VoucherOrderDetailDTO detail = new VoucherOrderDetailDTO();
        detail.setOrderId("1001");
        detail.setStatus(status);
        detail.setPayType(1);
        detail.setCreateTime(createTime);
        detail.setExpireTime(createTime.plusMinutes(5));
        return detail;
    }
}
