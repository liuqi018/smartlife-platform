package com.smartlife.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Information exposed to a user for one of their voucher orders. */
@Data
public class VoucherOrderDetailDTO {
    /** String on purpose: RedisIdWorker IDs exceed JavaScript's safe integer range. */
    private String orderId;
    private Long voucherId;
    private Integer voucherType;
    private Long shopId;
    private String voucherTitle;
    private String shopName;
    private String shopImage;
    private Long payValue;
    private Long actualValue;
    private Integer status;
    private Integer payType;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime refundTime;
    private LocalDateTime expireTime;
}
