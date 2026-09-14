USE `hmdp_benchmark`;

DELIMITER $$

DROP PROCEDURE IF EXISTS `hmdp_benchmark`.`assert_voucher_order_benchmark_ready`$$

CREATE PROCEDURE `hmdp_benchmark`.`assert_voucher_order_benchmark_ready`()
BEGIN
    IF DATABASE() <> 'hmdp_benchmark' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing to run outside hmdp_benchmark';
    END IF;

    IF (SELECT COUNT(*) FROM `hmdp_benchmark`.`tb_user`) < 1000 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'At least 1000 copied users are required';
    END IF;

    IF (
           SELECT COUNT(*)
           FROM `hmdp_benchmark`.`tb_voucher` v
                    INNER JOIN `hmdp_benchmark`.`tb_shop` s
                               ON s.id = v.shop_id
           WHERE v.sub_title IS NULL
              OR v.sub_title <> '[BENCHMARK_ORDER_DATA]'
       ) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No voucher template with a valid shop relation';
    END IF;

END$$

DELIMITER ;


CALL `hmdp_benchmark`.`assert_voucher_order_benchmark_ready`();

DROP PROCEDURE `hmdp_benchmark`.`assert_voucher_order_benchmark_ready`;


SET @benchmark_now := CURRENT_TIMESTAMP;
SET @benchmark_order_id_base := 880000000000000000;


START TRANSACTION;


/*
  生成数字辅助表
  修改：
  TEMPORARY TABLE -> 普通表
  避免 MySQL Can't reopen table 问题
*/


DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_digits`;

CREATE TABLE `hmdp_benchmark`.`benchmark_digits`
(
    `n` tinyint UNSIGNED NOT NULL,
    PRIMARY KEY (`n`)
) ENGINE=InnoDB;


INSERT INTO `hmdp_benchmark`.`benchmark_digits`
(`n`)
VALUES
    (0),(1),(2),(3),(4),
    (5),(6),(7),(8),(9);



DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_sequence`;

CREATE TABLE `hmdp_benchmark`.`benchmark_sequence`
(
    `seq` int UNSIGNED NOT NULL,
    PRIMARY KEY (`seq`)
) ENGINE=InnoDB;



INSERT INTO `hmdp_benchmark`.`benchmark_sequence`
(`seq`)
SELECT
    ones.n
        + tens.n * 10
        + hundreds.n * 100
        + thousands.n * 1000
        + 1

FROM `hmdp_benchmark`.`benchmark_digits` ones

         CROSS JOIN `hmdp_benchmark`.`benchmark_digits` tens

         CROSS JOIN `hmdp_benchmark`.`benchmark_digits` hundreds

         CROSS JOIN `hmdp_benchmark`.`benchmark_digits` thousands

WHERE
    ones.n
        + tens.n * 10
        + hundreds.n * 100
        + thousands.n * 1000 < 3000;



DELETE FROM `hmdp_benchmark`.`tb_voucher_order`
WHERE id > @benchmark_order_id_base
  AND id <= @benchmark_order_id_base + 10003000;



DELETE FROM `hmdp_benchmark`.`tb_voucher`
WHERE sub_title='[BENCHMARK_ORDER_DATA]';



/*
  创建3000张历史优惠券
*/

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_voucher_templates`;

CREATE TABLE `hmdp_benchmark`.`benchmark_voucher_templates`
AS
SELECT
    v.*,
    ROW_NUMBER() OVER (ORDER BY v.id) AS template_rank,
    COUNT(*) OVER () AS template_count

FROM `hmdp_benchmark`.`tb_voucher` v

         INNER JOIN `hmdp_benchmark`.`tb_shop` s
                    ON s.id=v.shop_id;



INSERT INTO `hmdp_benchmark`.`tb_voucher`
(
    shop_id,
    title,
    sub_title,
    rules,
    pay_value,
    actual_value,
    type,
    status,
    create_time,
    update_time
)

SELECT

    t.shop_id,

    CONCAT(
            LEFT(t.title,235),
            '-batch-',
            LPAD(q.seq,4,'0')
    ),

    '[BENCHMARK_ORDER_DATA]',

    t.rules,

    t.pay_value,

    t.actual_value,

    1,

    3,

    TIMESTAMPADD(
            HOUR,
            -q.seq,
            @benchmark_now
    ),

    TIMESTAMPADD(
            HOUR,
            -q.seq,
            @benchmark_now
    )


FROM `hmdp_benchmark`.`benchmark_sequence` q

         INNER JOIN `hmdp_benchmark`.`benchmark_voucher_templates` t

                    ON t.template_rank=
                       1+MOD(q.seq-1,t.template_count);



/*
 用户分层
*/

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_users`;

CREATE TABLE `hmdp_benchmark`.`benchmark_users`
AS

SELECT

    id AS user_id,

    ROW_NUMBER() OVER(ORDER BY id) AS user_rank

FROM `hmdp_benchmark`.`tb_user`

ORDER BY id

LIMIT 1000;



/*
 benchmark voucher
*/

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_vouchers`;

CREATE TABLE `hmdp_benchmark`.`benchmark_vouchers`
AS

SELECT

    id AS voucher_id,

    ROW_NUMBER() OVER(ORDER BY id) AS voucher_rank

FROM `hmdp_benchmark`.`tb_voucher`

WHERE sub_title='[BENCHMARK_ORDER_DATA]';



/*
 生成订单基础数据
*/

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_order_base`;

CREATE TABLE `hmdp_benchmark`.`benchmark_order_base`
AS

SELECT

    u.user_id,

    u.user_rank,

    q.seq AS user_order_seq,

    v.voucher_id,

    u.user_rank*10000+q.seq AS deterministic_no,

    u.user_rank*37+q.seq*61 AS distribution_no,


    CASE

        WHEN MOD(u.user_rank*37+q.seq*61,100)<15
            THEN 1

        WHEN MOD(u.user_rank*37+q.seq*61,100)<50
            THEN 2

        WHEN MOD(u.user_rank*37+q.seq*61,100)<70
            THEN 3

        WHEN MOD(u.user_rank*37+q.seq*61,100)<90
            THEN 4

        WHEN MOD(u.user_rank*37+q.seq*61,100)<93
            THEN 5

        ELSE 6

        END AS order_status


FROM `hmdp_benchmark`.`benchmark_users` u

         INNER JOIN `hmdp_benchmark`.`benchmark_sequence` q

                    ON q.seq <=

                       CASE

                           WHEN u.user_rank<=10 THEN 3000

                           WHEN u.user_rank<=100 THEN 300

                           WHEN u.user_rank<=800 THEN 48

                           ELSE 47

                           END


         INNER JOIN `hmdp_benchmark`.`benchmark_vouchers` v

                    ON v.voucher_rank=q.seq;



/*
 时间
*/

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_order_time`;

CREATE TABLE `hmdp_benchmark`.`benchmark_order_time`
AS

SELECT

    b.*,

    CASE

        WHEN b.order_status=1
            AND MOD(b.distribution_no,5)=0

            THEN TIMESTAMPADD(
                SECOND,
                -(30+MOD(b.distribution_no*17,240)),
                @benchmark_now
                 )


        WHEN MOD(b.distribution_no,10)<5

            THEN TIMESTAMPADD(
                SECOND,
                -(301+MOD(b.distribution_no*97,2591699)),
                @benchmark_now
                 )


        WHEN MOD(b.distribution_no,10)<8

            THEN TIMESTAMPADD(
                SECOND,
                -(2678400+MOD(b.distribution_no*89,5184000)),
                @benchmark_now
                 )


        ELSE

            TIMESTAMPADD(
                    SECOND,
                    -(7862400+MOD(b.distribution_no*83,7776000)),
                    @benchmark_now
            )

        END AS order_create_time


FROM `hmdp_benchmark`.`benchmark_order_base` b;



/*
 插入10万订单
*/

INSERT INTO `hmdp_benchmark`.`tb_voucher_order`
(
    id,
    user_id,
    voucher_id,
    pay_type,
    status,
    create_time,
    pay_time,
    use_time,
    refund_time,
    update_time
)

SELECT


    @benchmark_order_id_base + deterministic_no,

    user_id,

    voucher_id,

    1+MOD(distribution_no,3),

    order_status,

    order_create_time,


    CASE

        WHEN order_status IN(2,3,5,6)

            THEN TIMESTAMPADD(
                SECOND,
                30+MOD(distribution_no,240),
                order_create_time
                 )

        ELSE NULL

        END,


    CASE

        WHEN order_status=3

            THEN TIMESTAMPADD(
                DAY,
                1+MOD(distribution_no,7),
                order_create_time
                 )

        ELSE NULL

        END,


    CASE

        WHEN order_status=6

            THEN TIMESTAMPADD(
                DAY,
                2+MOD(distribution_no,5),
                order_create_time
                 )

        ELSE NULL

        END,


    CASE

        WHEN order_status=3

            THEN TIMESTAMPADD(
                DAY,
                1+MOD(distribution_no,7),
                order_create_time
                 )

        WHEN order_status=6

            THEN TIMESTAMPADD(
                DAY,
                2+MOD(distribution_no,5),
                order_create_time
                 )

        WHEN order_status=5

            THEN TIMESTAMPADD(
                DAY,
                1,
                order_create_time
                 )

        ELSE order_create_time

        END


FROM `hmdp_benchmark`.`benchmark_order_time`;


COMMIT;



/*
 清理辅助表
*/

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_order_time`;

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_order_base`;

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_vouchers`;

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_users`;

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_voucher_templates`;

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_sequence`;

DROP TABLE IF EXISTS `hmdp_benchmark`.`benchmark_digits`;



SELECT COUNT(*) AS generated_orders
FROM `hmdp_benchmark`.`tb_voucher_order`;


SELECT

    status,

    COUNT(*) AS orders,

    ROUND(
            COUNT(*)*100.0/100000,
            2
    ) AS percentage


FROM `hmdp_benchmark`.`tb_voucher_order`

GROUP BY status

ORDER BY status;