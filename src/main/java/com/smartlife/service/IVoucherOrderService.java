package com.smartlife.service;

import com.smartlife.dto.Result;
import com.smartlife.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createNormalOrder(Long voucherId);

    Result queryOrderDetail(Long orderId);

    Result mockPay(Long orderId);

    Result cancelOrder(Long orderId);

    Result refundOrder(Long orderId);

    Result queryMyOrders(Integer current, Integer size, Integer status);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
