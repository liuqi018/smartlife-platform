package com.smartlife.mapper;

import com.smartlife.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.dto.VoucherOrderDetailDTO;
import com.smartlife.agent.dto.OrderStatusCounts;
import com.smartlife.agent.dto.OrderTrendRow;
import com.smartlife.agent.dto.VoucherRankingRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author liuqi
 * @since 2025-11-21
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Select("SELECT id FROM tb_voucher_order " +
            "WHERE user_id = #{userId} AND voucher_id = #{voucherId} AND status IN (1, 2) " +
            "ORDER BY create_time ASC LIMIT 1")
    Long selectActiveOrderId(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

    @Select("SELECT o.id AS order_id, o.voucher_id, v.shop_id, v.title AS voucher_title, " +
            "v.type AS voucher_type, v.pay_value, v.actual_value, o.status, o.pay_type, " +
            "o.create_time, o.pay_time, o.refund_time, " +
            "DATE_ADD(o.create_time, INTERVAL 5 MINUTE) AS expire_time, " +
            "s.name AS shop_name, s.images AS shop_image " +
            "FROM tb_voucher_order o " +
            "JOIN tb_voucher v ON v.id = o.voucher_id " +
            "JOIN tb_shop s ON s.id = v.shop_id " +
            "WHERE o.id = #{orderId} AND o.user_id = #{userId}")
    VoucherOrderDetailDTO selectDetailForUser(@Param("orderId") Long orderId,
                                               @Param("userId") Long userId);

    @Update("UPDATE tb_voucher_order SET status = 2, pay_time = #{payTime}, pay_type = 1 " +
            "WHERE id = #{orderId} AND user_id = #{userId} AND status = 1 " +
            "AND #{payTime} <= DATE_ADD(create_time, INTERVAL 5 MINUTE)")
    int markPaid(@Param("orderId") Long orderId, @Param("userId") Long userId,
                 @Param("payTime") LocalDateTime payTime);

    @Update("UPDATE tb_voucher_order SET status = 4 " +
            "WHERE id = #{orderId} AND user_id = #{userId} AND status = 1")
    int cancelUnpaid(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Update("UPDATE tb_voucher_order SET status = 4 WHERE status = 1 " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
    int cancelAllExpiredUnpaid();

    @Update("UPDATE tb_voucher_order SET status = 4 " +
            "WHERE user_id = #{userId} AND voucher_id = #{voucherId} AND status = 1 " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
    int cancelExpiredForVoucher(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

    @Update("UPDATE tb_voucher_order SET status = 4 WHERE user_id = #{userId} AND status = 1 " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
    int cancelExpiredForUser(@Param("userId") Long userId);

    @Update("UPDATE tb_voucher_order SET status = 5 " +
            "WHERE id = #{orderId} AND user_id = #{userId} AND status = 2")
    int markRefunding(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Update("UPDATE tb_voucher_order SET status = 6, refund_time = #{refundTime} " +
            "WHERE id = #{orderId} AND user_id = #{userId} AND status = 5")
    int markRefunded(@Param("orderId") Long orderId, @Param("userId") Long userId,
                     @Param("refundTime") LocalDateTime refundTime);

    @Select("<script>SELECT o.id AS order_id, o.voucher_id, v.type AS voucher_type, " +
            "v.title AS voucher_title, v.shop_id, s.name AS shop_name, s.images AS shop_image, " +
            "v.pay_value, v.actual_value, o.status, o.pay_type, o.create_time, o.pay_time, " +
            "o.refund_time, DATE_ADD(o.create_time, INTERVAL 5 MINUTE) AS expire_time " +
            "FROM tb_voucher_order o JOIN tb_voucher v ON v.id = o.voucher_id " +
            "JOIN tb_shop s ON s.id = v.shop_id WHERE o.user_id = #{userId} " +
            "<if test='status != null'>AND o.status = #{status} </if>" +
            "ORDER BY o.create_time DESC LIMIT #{offset}, #{size}</script>")
    List<VoucherOrderDetailDTO> selectUserOrders(@Param("userId") Long userId,
                                                  @Param("status") Integer status,
                                                  @Param("offset") long offset,
                                                  @Param("size") long size);

    @Select("<script>SELECT COUNT(*) FROM tb_voucher_order " +
            "WHERE user_id = #{userId} " +
            "<if test='status != null'>AND status = #{status}</if></script>")
    long countUserOrders(@Param("userId") Long userId, @Param("status") Integer status);

    // The ownership join is part of every aggregate, not merely a Java pre-check.
    @Select("SELECT COUNT(*) AS totalOrders, " +
            "COALESCE(SUM(o.status = 1),0) AS unpaidOrders, COALESCE(SUM(o.status = 2),0) AS paidOrders, " +
            "COALESCE(SUM(o.status = 3),0) AS usedOrders, COALESCE(SUM(o.status = 4),0) AS cancelledOrders, " +
            "COALESCE(SUM(o.status = 5),0) AS refundingOrders, COALESCE(SUM(o.status = 6),0) AS refundedOrders " +
            "FROM tb_shop_owner so JOIN tb_voucher v ON v.shop_id = so.shop_id " +
            "JOIN tb_voucher_order o ON o.voucher_id = v.id " +
            "WHERE so.owner_user_id = #{userId} AND so.shop_id = #{shopId} " +
            "AND o.create_time >= #{start} AND o.create_time < #{end}")
    OrderStatusCounts countShopOrders(@Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT DATE_FORMAT(o.create_time, '%Y-%m-%d') AS day, COUNT(*) AS orderCount " +
            "FROM tb_shop_owner so JOIN tb_voucher v ON v.shop_id = so.shop_id " +
            "JOIN tb_voucher_order o ON o.voucher_id = v.id " +
            "WHERE so.owner_user_id = #{userId} AND so.shop_id = #{shopId} " +
            "AND o.create_time >= #{start} AND o.create_time < #{end} " +
            "GROUP BY DATE_FORMAT(o.create_time, '%Y-%m-%d') ORDER BY day")
    List<OrderTrendRow> shopOrderDailyTrend(@Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT v.id AS voucherId, v.title AS voucherTitle, v.type AS voucherType, " +
            "COUNT(*) AS totalOrders, COALESCE(SUM(o.status = 1),0) AS unpaidOrders, " +
            "COALESCE(SUM(o.status = 2),0) AS paidOrders, COALESCE(SUM(o.status = 3),0) AS usedOrders, " +
            "COALESCE(SUM(o.status = 4),0) AS cancelledOrders, COALESCE(SUM(o.status = 5),0) AS refundingOrders, " +
            "COALESCE(SUM(o.status = 6),0) AS refundedOrders " +
            "FROM tb_shop_owner so JOIN tb_voucher v ON v.shop_id = so.shop_id " +
            "JOIN tb_voucher_order o ON o.voucher_id = v.id " +
            "WHERE so.owner_user_id = #{userId} AND so.shop_id = #{shopId} " +
            "AND o.create_time >= #{start} AND o.create_time < #{end} " +
            "GROUP BY v.id, v.title, v.type ORDER BY totalOrders DESC, v.id ASC LIMIT #{limit}")
    List<VoucherRankingRow> shopVoucherRanking(@Param("userId") Long userId, @Param("shopId") Long shopId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("limit") int limit);

}
