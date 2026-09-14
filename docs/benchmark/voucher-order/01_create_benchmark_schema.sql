CREATE DATABASE IF NOT EXISTS `hmdp_benchmark`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

DROP TABLE IF EXISTS `hmdp_benchmark`.`tb_voucher_order`;
DROP TABLE IF EXISTS `hmdp_benchmark`.`tb_voucher`;
DROP TABLE IF EXISTS `hmdp_benchmark`.`tb_shop`;
DROP TABLE IF EXISTS `hmdp_benchmark`.`tb_user`;

CREATE TABLE `hmdp_benchmark`.`tb_user` LIKE `hmdp`.`tb_user`;
CREATE TABLE `hmdp_benchmark`.`tb_shop` LIKE `hmdp`.`tb_shop`;
CREATE TABLE `hmdp_benchmark`.`tb_voucher` LIKE `hmdp`.`tb_voucher`;

CREATE TABLE `hmdp_benchmark`.`tb_voucher_order` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `voucher_id` bigint(20) UNSIGNED NOT NULL,
  `pay_type` tinyint(1) UNSIGNED NOT NULL DEFAULT 1,
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time` timestamp NULL DEFAULT NULL,
  `use_time` timestamp NULL DEFAULT NULL,
  `refund_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB
  CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_general_ci
  ROW_FORMAT=Compact;

INSERT INTO `hmdp_benchmark`.`tb_user`
SELECT * FROM `hmdp`.`tb_user`;

INSERT INTO `hmdp_benchmark`.`tb_shop`
SELECT * FROM `hmdp`.`tb_shop`;

INSERT INTO `hmdp_benchmark`.`tb_voucher`
SELECT * FROM `hmdp`.`tb_voucher`;

SELECT 'tb_user' AS table_name, COUNT(*) AS copied_rows
FROM `hmdp_benchmark`.`tb_user`
UNION ALL
SELECT 'tb_shop', COUNT(*) FROM `hmdp_benchmark`.`tb_shop`
UNION ALL
SELECT 'tb_voucher', COUNT(*) FROM `hmdp_benchmark`.`tb_voucher`
UNION ALL
SELECT 'tb_voucher_order', COUNT(*) FROM `hmdp_benchmark`.`tb_voucher_order`;

SHOW INDEX FROM `hmdp_benchmark`.`tb_voucher_order`;
