USE `hmdp_benchmark`;

SELECT 'tb_user' AS table_name, COUNT(*) AS row_count
FROM `hmdp_benchmark`.`tb_user`
UNION ALL
SELECT 'tb_shop', COUNT(*) FROM `hmdp_benchmark`.`tb_shop`
UNION ALL
SELECT 'tb_voucher', COUNT(*) FROM `hmdp_benchmark`.`tb_voucher`
UNION ALL
SELECT 'tb_voucher_order', COUNT(*) FROM `hmdp_benchmark`.`tb_voucher_order`;

SHOW INDEX FROM `hmdp_benchmark`.`tb_voucher_order`;

SELECT status,
       COUNT(*) AS order_count,
       ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS percentage
FROM `hmdp_benchmark`.`tb_voucher_order`
GROUP BY status
ORDER BY status;

SELECT order_count, COUNT(*) AS user_count
FROM (
  SELECT user_id, COUNT(*) AS order_count
  FROM `hmdp_benchmark`.`tb_voucher_order`
  GROUP BY user_id
) x
GROUP BY order_count
ORDER BY order_count DESC;

SELECT user_id, voucher_id, COUNT(*) AS duplicate_count
FROM `hmdp_benchmark`.`tb_voucher_order`
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1
LIMIT 20;

SELECT COUNT(*) AS missing_user_orders
FROM `hmdp_benchmark`.`tb_voucher_order` o
LEFT JOIN `hmdp_benchmark`.`tb_user` u ON u.id = o.user_id
WHERE u.id IS NULL;

SELECT COUNT(*) AS missing_voucher_orders
FROM `hmdp_benchmark`.`tb_voucher_order` o
LEFT JOIN `hmdp_benchmark`.`tb_voucher` v ON v.id = o.voucher_id
WHERE v.id IS NULL;

SELECT COUNT(*) AS invalid_shop_orders
FROM `hmdp_benchmark`.`tb_voucher_order` o
INNER JOIN `hmdp_benchmark`.`tb_voucher` v ON v.id = o.voucher_id
LEFT JOIN `hmdp_benchmark`.`tb_shop` s ON s.id = v.shop_id
WHERE s.id IS NULL;

SELECT MIN(create_time) AS oldest_order,
       MAX(create_time) AS newest_order,
       COUNT(*) AS total_orders
FROM `hmdp_benchmark`.`tb_voucher_order`;

SELECT CASE
         WHEN create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) THEN '00-30 days'
         WHEN create_time >= DATE_SUB(NOW(), INTERVAL 90 DAY) THEN '31-90 days'
         ELSE '91-180 days'
       END AS time_bucket,
       COUNT(*) AS order_count,
       ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS percentage
FROM `hmdp_benchmark`.`tb_voucher_order`
GROUP BY time_bucket
ORDER BY time_bucket;

SELECT CASE
         WHEN create_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE) THEN 'expired'
         ELSE 'within 5 minutes'
       END AS unpaid_bucket,
       COUNT(*) AS order_count
FROM `hmdp_benchmark`.`tb_voucher_order`
WHERE status = 1
GROUP BY unpaid_bucket;

SELECT
  SUM(status IN (2, 3, 5, 6) AND (pay_time IS NULL OR pay_time < create_time)) AS invalid_pay_time,
  SUM(status = 3 AND (use_time IS NULL OR use_time < pay_time)) AS invalid_use_time,
  SUM(status = 6 AND (refund_time IS NULL OR refund_time < pay_time)) AS invalid_refund_time,
  SUM(
    status IN (1, 4)
    AND (pay_time IS NOT NULL OR use_time IS NOT NULL OR refund_time IS NOT NULL)
  ) AS invalid_unpaid_or_cancelled_time
FROM `hmdp_benchmark`.`tb_voucher_order`;

SELECT user_id, COUNT(*) AS order_count
FROM `hmdp_benchmark`.`tb_voucher_order`
GROUP BY user_id
ORDER BY order_count DESC, user_id
LIMIT 20;
