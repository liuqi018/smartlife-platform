package com.smartlife.controller;


import com.smartlife.dto.Result;
import com.smartlife.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @PostMapping("normal/{voucherId}")
    public Result createNormalOrder(@PathVariable Long voucherId) {
        return voucherOrderService.createNormalOrder(voucherId);
    }

    @GetMapping("{orderId}")
    public Result queryOrderDetail(@PathVariable Long orderId) {
        return voucherOrderService.queryOrderDetail(orderId);
    }

    /** Development/demo endpoint. It does not contact a real payment provider. */
    @PostMapping("{orderId}/mock-pay")
    public Result mockPay(@PathVariable Long orderId) {
        return voucherOrderService.mockPay(orderId);
    }

    @PostMapping("{orderId}/cancel")
    public Result cancelOrder(@PathVariable Long orderId) {
        return voucherOrderService.cancelOrder(orderId);
    }

    @PostMapping("{orderId}/refund")
    public Result refundOrder(@PathVariable Long orderId) {
        return voucherOrderService.refundOrder(orderId);
    }

    @GetMapping("me")
    public Result queryMyOrders(@RequestParam(defaultValue = "1") Integer current,
                                @RequestParam(defaultValue = "10") Integer size,
                                @RequestParam(required = false) Integer status) {
        return voucherOrderService.queryMyOrders(current, size, status);
    }
}
