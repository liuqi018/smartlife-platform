-- SmartLife 南京演示内容增量数据
-- 适配 hmdp.sql 当前表结构。包含：演示用户、用户信息、普通优惠券、18 家南京商户探店笔记、笔记评论。
-- 说明：所有内容均为项目演示数据，不代表真实线上评价。
-- 秒杀券未在本文件中直接插入，因为秒杀还依赖 Redis seckill:stock:{voucherId}；请使用现有 POST /voucher/seckill 创建。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

-- 1. 创建 4 个南京演示用户（手机号仅用于本地演示）
INSERT INTO tb_user(phone,password,nick_name,icon) VALUES ('19900002001','','南信大吃喝指南','/imgs/icons/kkjtbcr.jpg') ON DUPLICATE KEY UPDATE nick_name=VALUES(nick_name), icon=VALUES(icon);
SELECT id INTO @u1 FROM tb_user WHERE phone='19900002001' LIMIT 1;
DELETE FROM tb_user_info WHERE user_id=@u1;
INSERT INTO tb_user_info(user_id,city,introduce,fans,followee,gender,birthday,credits,level) VALUES (@u1,'南京','喜欢记录南京江北附近的吃喝玩乐。',128,42,1,'1999-05-12',680,5);
INSERT INTO tb_user(phone,password,nick_name,icon) VALUES ('19900002002','','江北周末计划','/imgs/icons/user5-icon.png') ON DUPLICATE KEY UPDATE nick_name=VALUES(nick_name), icon=VALUES(icon);
SELECT id INTO @u2 FROM tb_user WHERE phone='19900002002' LIMIT 1;
DELETE FROM tb_user_info WHERE user_id=@u2;
INSERT INTO tb_user_info(user_id,city,introduce,fans,followee,gender,birthday,credits,level) VALUES (@u2,'南京','周末探店、运动和休闲体验分享。',96,31,0,'1998-11-03',520,4);
INSERT INTO tb_user(phone,password,nick_name,icon) VALUES ('19900002003','','今天吃什么呀','/imgs/blogs/blog1.jpg') ON DUPLICATE KEY UPDATE nick_name=VALUES(nick_name), icon=VALUES(icon);
SELECT id INTO @u3 FROM tb_user WHERE phone='19900002003' LIMIT 1;
DELETE FROM tb_user_info WHERE user_id=@u3;
INSERT INTO tb_user_info(user_id,city,introduce,fans,followee,gender,birthday,credits,level) VALUES (@u3,'南京','美食和饮品爱好者，偏爱性价比小店。',205,67,1,'2000-02-18',920,6);
INSERT INTO tb_user(phone,password,nick_name,icon) VALUES ('19900002004','','小北同学','/imgs/icons/kkjtbcr.jpg') ON DUPLICATE KEY UPDATE nick_name=VALUES(nick_name), icon=VALUES(icon);
SELECT id INTO @u4 FROM tb_user WHERE phone='19900002004' LIMIT 1;
DELETE FROM tb_user_info WHERE user_id=@u4;
INSERT INTO tb_user_info(user_id,city,introduce,fans,followee,gender,birthday,credits,level) VALUES (@u4,'南京','学生党日常，记录学校周边生活。',73,28,0,'2001-08-26',410,3);

-- 2. 普通优惠券：有真实券才展示，均为 type=0 / status=1
DELETE FROM tb_voucher WHERE shop_id BETWEEN 15 AND 32 AND title LIKE '[南京演示]%';
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (15,'[南京演示]100元烧烤代金券','周一至周日可用','仅限堂食；每桌限用1张；不可兑现',7900,10000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (17,'[南京演示]30元快餐代金券','全天可用','单笔订单限用1张；不可与其他优惠叠加',2500,3000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (18,'[南京演示]100元欢唱代金券','周一至周四可用','需提前预约；节假日以门店规则为准',7800,10000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (21,'[南京演示]120元美甲代金券','基础款可用','部分复杂款式需补差价',8800,12000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (24,'[南京演示]50元健身体验券','首次到店可用','限首次体验；每人限用1张',1990,5000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (26,'[南京演示]200元SPA代金券','预约后使用','需提前预约；特殊项目除外',15800,20000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (30,'[南京演示]20元咖啡代金券','指定饮品可用','单笔订单限用1张',1500,2000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (31,'[南京演示]20元饮品代金券','全场饮品可用','每单限用1张；冰淇淋除外',1290,2000,0,1);
INSERT INTO tb_voucher(shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES (32,'[南京演示]100元鸡公煲代金券','堂食可用','每桌限用1张；不可兑现',6900,10000,0,1);

-- 3. 清理本文件曾生成的探店笔记及其评论，保证可重复导入
DELETE c FROM tb_blog_comments c INNER JOIN tb_blog b ON c.blog_id=b.id WHERE b.title LIKE '[南京演示]%';
DELETE FROM tb_blog WHERE title LIKE '[南京演示]%';

-- 4. 每家南京商户生成 1 条与商户类型匹配的探店笔记 + 2 条评论
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (15,@u1,'[南京演示]南京烧烤探店｜炭火香很足，适合朋友聚餐','/imgs/shop/nanjing/tianliang-bbq.png','晚上来吃更有氛围，炭火烤串香气很足。肉串火候控制得不错，外焦里嫩，适合三五个人一起聚餐。整体口味偏香辣，出餐速度也可以。',22,2);
SET @b15 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b15,0,0,'炭火味挺香的，晚上和朋友来吃很合适。',3,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b15,0,0,'串的火候不错，整体分量也够。',1,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (16,@u2,'[南京演示]家常菜探店｜柴火炒鸡很下饭','/imgs/shop/nanjing/yimeng-chicken.png','鸡肉炖得比较入味，酱香明显，配米饭很合适。份量适合两到三个人分享，喜欢家常口味的话可以尝试。',29,2);
SET @b16 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b16,0,0,'鸡肉很入味，配米饭很下饭。',4,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b16,0,0,'家常口味，适合两三个人一起吃。',2,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (17,@u3,'[南京演示]工作日快餐｜出餐快，选择也比较多','/imgs/shop/nanjing/laoxiangji.png','适合赶时间的时候来吃，套餐搭配比较清楚，出餐速度快。口味偏家常，价格也比较容易接受。',36,2);
SET @b17 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b17,0,0,'出餐确实快，工作日吃很方便。',5,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b17,0,0,'套餐选择比较多，价格也比较合适。',3,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (18,@u4,'[南京演示]KTV体验｜包厢整洁，朋友聚会够用','/imgs/shop/nanjing/maikachun-ktv.png','包厢空间和灯光效果都不错，音响表现适合日常聚会。多人一起去更划算，整体氛围比较轻松。',43,2);
SET @b18 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b18,0,0,'包厢挺干净的，朋友聚会够用了。',6,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b18,0,0,'音响效果不错，整体体验可以。',4,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (19,@u1,'[南京演示]美发体验｜沟通顺畅，造型比较自然','/imgs/shop/nanjing/youxuan-hair.png','剪发前会先沟通想要的长度和风格，过程比较细致。最终造型偏自然，日常打理也比较方便。',50,2);
SET @b19 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b19,0,0,'设计师沟通很耐心，最后效果比较自然。',7,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b19,0,0,'没有一直推销项目，这点不错。',5,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (20,@u2,'[南京演示]造型体验｜环境简洁，服务节奏舒服','/imgs/shop/nanjing/tengye-hair.png','店内环境干净，设计师会根据脸型和日常需求给建议。整体服务节奏舒服，不会一直推销项目。',57,2);
SET @b20 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b20,0,0,'环境挺简洁，剪完比较好打理。',8,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b20,0,0,'服务节奏舒服，整体体验不错。',6,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (21,@u3,'[南京演示]日式美甲体验｜款式精致，细节处理不错','/imgs/shop/nanjing/nice-nail.png','可选颜色和款式比较丰富，做出来的细节比较干净。适合喜欢简约或日系风格的人。',64,2);
SET @b21 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b21,0,0,'款式很精致，细节处理得挺好。',9,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b21,0,0,'颜色选择很多，日系风格很好看。',1,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (22,@u4,'[南京演示]美甲美睫体验｜选择多，整体完成度高','/imgs/shop/nanjing/iu-nail.png','到店后可以先看款式再决定，颜色选择比较多。操作过程细致，成品整体完成度不错。',71,2);
SET @b22 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b22,0,0,'美甲和美睫都可以一起做，比较方便。',10,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b22,0,0,'成品挺精致，过程也比较细致。',2,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (23,@u1,'[南京演示]健身工作室体验｜器械够用，训练氛围不错','/imgs/shop/nanjing/leo-fitness.png','器械配置对日常力量训练够用，空间不算特别大但比较整洁。适合想规律训练的人。',78,2);
SET @b23 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b23,0,0,'器械够用，训练氛围不错。',3,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b23,0,0,'适合自己有训练计划的人。',3,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (24,@u2,'[南京演示]健身体验｜器械类型齐，时间安排灵活','/imgs/shop/nanjing/leke-fitness.png','有氧和力量器械都比较齐全，训练区域划分清楚。适合自己有训练计划、想灵活安排时间的人。',85,2);
SET @b24 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b24,0,0,'器械种类比较齐，时间安排很灵活。',4,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b24,0,0,'环境不错，日常训练很方便。',4,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (25,@u3,'[南京演示]夜生活探店｜氛围感在线，适合小聚','/imgs/shop/nanjing/miye-bar.png','晚上灯光氛围比较好，适合两三个人聊天小聚。饮品选择够用，整体环境偏安静放松。',92,2);
SET @b25 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b25,0,0,'晚上氛围不错，适合聊天。',5,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b25,0,0,'饮品选择还可以，整体比较安静。',5,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (26,@u4,'[南京演示]SPA体验｜环境安静，适合放松','/imgs/shop/nanjing/baolilai-spa.png','店内整体环境比较安静，芳疗过程节奏舒缓。适合周末想休息一下、放松肩颈的时候去。',99,2);
SET @b26 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b26,0,0,'环境很安静，做完比较放松。',6,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b26,0,0,'服务比较细致，适合周末休息。',6,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (27,@u1,'[南京演示]头疗体验｜放松感明显，服务比较细致','/imgs/shop/nanjing/weiyun-massage.png','头疗过程比较舒适，肩颈也会做一些基础放松。整体节奏不赶，适合久坐之后去放松。',106,2);
SET @b27 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b27,0,0,'头疗挺舒服，肩颈也放松了不少。',7,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b27,0,0,'整体不赶时间，体验比较轻松。',1,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (28,@u2,'[南京演示]亲子周末去处｜项目丰富，孩子比较容易玩开','/imgs/shop/nanjing/lukachi-family.png','游乐项目比较集中，适合亲子周末消磨半天时间。场地整体明亮，家长陪同也比较方便。',22,2);
SET @b28 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b28,0,0,'孩子玩得很开心，项目挺集中。',8,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b28,0,0,'周末带娃来比较合适。',2,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (29,@u3,'[南京演示]朋友聚会｜桌游娱乐项目比较全','/imgs/shop/nanjing/shidai-party.png','适合生日、同学聚会或者小型团建，桌游和娱乐项目比较集中。多人一起去氛围更好。',29,2);
SET @b29 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b29,0,0,'朋友聚会很合适，桌游选择不少。',9,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b29,0,0,'多人一起玩比较有氛围。',3,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (30,@u4,'[南京演示]校园咖啡｜出杯快，学习间隙很方便','/imgs/shop/nanjing/luckin-coffee.png','离校园比较近，线上下单后取餐方便。咖啡选择稳定，适合上课或学习间隙买一杯。',36,2);
SET @b30 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u1,@b30,0,0,'出杯很快，上课前买一杯很方便。',10,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b30,0,0,'离学校近，日常喝比较省事。',4,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (31,@u1,'[南京演示]学生党饮品｜价格友好，日常随手买','/imgs/shop/nanjing/mixue-tea.png','价格比较友好，常见果茶和冰淇淋都有。天气热的时候买一杯很方便，适合学生日常消费。',43,2);
SET @b31 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u2,@b31,0,0,'价格很友好，学生党经常买。',3,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b31,0,0,'天气热的时候来杯果茶很舒服。',5,0);
INSERT INTO tb_blog(shop_id,user_id,title,images,content,liked,comments) VALUES (32,@u2,'[南京演示]快餐探店｜香辣下饭，适合多人分享','/imgs/shop/nanjing/chongqing-jigongbao.png','鸡肉比较入味，汤汁拌饭很香。喜欢香辣口味的人会比较合适，两三个人一起点更方便。',50,2);
SET @b32 = LAST_INSERT_ID();
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u3,@b32,0,0,'味道香辣，拌饭特别合适。',4,0);
INSERT INTO tb_blog_comments(user_id,blog_id,parent_id,answer_id,content,liked,status) VALUES (@u4,@b32,0,0,'两三个人一起吃份量比较够。',6,0);

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

-- 5. 导入后检查
SELECT COUNT(*) AS nanjing_demo_blogs FROM tb_blog WHERE title LIKE '[南京演示]%';
SELECT b.shop_id, s.name, b.title, b.comments FROM tb_blog b JOIN tb_shop s ON s.id=b.shop_id WHERE b.title LIKE '[南京演示]%' ORDER BY b.shop_id;
SELECT shop_id,title,type,status FROM tb_voucher WHERE shop_id BETWEEN 15 AND 32 AND title LIKE '[南京演示]%' ORDER BY shop_id;
SELECT COUNT(*) AS nanjing_demo_comments FROM tb_blog_comments c JOIN tb_blog b ON b.id=c.blog_id WHERE b.title LIKE '[南京演示]%';
