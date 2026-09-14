package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.smartlife.entity.SeckillVoucher;
import com.smartlife.entity.VoucherOrder;
import com.smartlife.mapper.SeckillVoucherMapper;
import com.smartlife.mapper.VoucherOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class SeckillOrderPersistenceService {
    @Resource private VoucherOrderMapper voucherOrderMapper;
    @Resource private SeckillVoucherMapper seckillVoucherMapper;

    @Transactional(rollbackFor = Exception.class)
    public void persist(VoucherOrder order) {
        Integer count = voucherOrderMapper.selectCount(new QueryWrapper<VoucherOrder>()
                .eq("user_id", order.getUserId()).eq("voucher_id", order.getVoucherId()));
        if (count != null && count > 0) return;
        int updated = seckillVoucherMapper.update(null, new UpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock - 1").eq("voucher_id", order.getVoucherId()).gt("stock", 0));
        if (updated != 1) throw new IllegalStateException("MySQL seckill stock is insufficient");
        if (voucherOrderMapper.insert(order) != 1) throw new IllegalStateException("Seckill order insert failed");
    }
}
