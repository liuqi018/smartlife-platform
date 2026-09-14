-- 南京商户演示数据（18条）
-- 来源：南京商户采集模板_图片文件名细分版(1).xlsx
-- 说明：本脚本仅新增 tb_shop 数据，不删除、不覆盖原有杭州商户。
-- 建议仅执行一次，重复执行会新增重复商户。

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

USE hmdp;

START TRANSACTION;

INSERT INTO tb_shop
(name, type_id, images, area, address, x, y, avg_price, sold, comments, score, open_hours)
VALUES
('天亮烧烤（大厂店）', 1, '/imgs/shop/nanjing/tianliang-bbq.png', '南信大周边', '南京市六合区新华西路326号桂馨园', 118.744800, 32.225900, 68, 1000, 25, 46, '17:00-02:00'),
('沂蒙柴火炒鸡', 1, '/imgs/shop/nanjing/yimeng-chicken.png', '南信大周边', '南京市六合区芳庭路与草芳路交叉口南30米', 118.724200, 32.210800, 98, 500, 20, 45, '10:30-21:30'),
('老乡鸡', 1, '/imgs/shop/nanjing/laoxiangji.png', '南信大周边', '浦口区盘金华府28栋路边门面店', 118.704800, 32.194800, 30, 300, 30, 44, '09:00-22:00'),
('麦卡纯', 2, '/imgs/shop/nanjing/maikachun-ktv.png', '南信大周边', '泰山街道火炬路华侨绿洲209', 118.698500, 32.183500, 42, 300, 20, 43, '12:00-02:00'),
('优选上品', 3, '/imgs/shop/nanjing/youxuan-hair.png', '南信大周边', '侨康路与朗山路交叉口东南侧20米', 118.713600, 32.203100, 98, 200, 10, 45, '10:00-21:30'),
('藤野造型', 3, '/imgs/shop/nanjing/tengye-hair.png', '南信大周边', '南京江北天街4F-27', 118.705900, 32.192300, 31, 600, 100, 46, '10:00-21:30'),
('Nice奈斯日式美甲美睫', 10, '/imgs/shop/nanjing/nice-nail.png', '南信大周边', '桥北弘阳时代中心二期7栋1520', 118.696800, 32.188200, 102, 200, 300, 45, '10:00-21:00'),
('iU美甲美睫', 10, '/imgs/shop/nanjing/iu-nail.png', '南信大周边', '弘阳壹号B座522室', 118.699100, 32.189500, 76, 300, 26, 47, '10:00-21:00'),
('Leo健身工作室', 4, '/imgs/shop/nanjing/leo-fitness.png', '南信大周边', '大桥北路弘阳时代中心二期4栋10楼1015', 118.697800, 32.187700, 199, 2000, 100, 48, '07:00-23:00'),
('乐刻运动健身', 4, '/imgs/shop/nanjing/leke-fitness.png', '南信大周边', '盘城街道探秘路72号活力源一楼', 118.718600, 32.208900, 1316, 5000, 200, 47, '07:00-23:00'),
('觅夜', 8, '/imgs/shop/nanjing/miye-bar.png', '南信大周边', '盘城街道南京扬子江国际会议中心龙山湖酒店', 118.715800, 32.202200, 56, 200, 15, 46, '18:00-02:00'),
('宝丽来芳疗SPA美容', 6, '/imgs/shop/nanjing/baolilai-spa.png', '南信大周边', '大桥北路大洋百货4楼', 118.700600, 32.190100, 339, 3000, 652, 47, '10:00-22:00'),
('微云头疗SPA', 5, '/imgs/shop/nanjing/weiyun-massage.png', '南信大周边', '泰山街道江北新区文景路99号', 118.706700, 32.197400, 82, 500, 314, 45, '10:00-24:00'),
('鲁卡奇亲子乐园', 7, '/imgs/shop/nanjing/lukachi-family.png', '南信大周边', '华欧大道8号', 118.731800, 32.218600, 173, 3000, 568, 44, '09:30-21:00'),
('时代轰趴馆', 9, '/imgs/shop/nanjing/shidai-party.png', '南信大周边', '时代中心一期4栋209室', 118.698800, 32.188800, 78, 200, 100, 45, '10:00-24:00'),
('瑞幸咖啡', 1, '/imgs/shop/nanjing/luckin-coffee.png', '南信大周边', '南京信息工程大学中苑', 118.711600, 32.205400, 10, 420, 401, 48, '08:00-22:00'),
('蜜雪冰城', 1, '/imgs/shop/nanjing/mixue-tea.png', '南信大周边', '南京信息工程大学西苑集中箱', 118.709900, 32.206700, 10, 3000, 600, 49, '08:00-22:00'),
('重庆鸡公煲', 1, '/imgs/shop/nanjing/chongqing-jigongbao.png', '南信大周边', '南京信息工程大学气象谷店', 118.713000, 32.204200, 25, 900, 600, 46, '09:00-22:00');

COMMIT;

-- 导入后验证：原有14条 + 新增18条时，总数应为32。
SELECT COUNT(*) AS total_shop_count FROM tb_shop;

-- 验证南京演示商户（按图片路径筛选）。
SELECT id, name, type_id, x, y, avg_price, score, open_hours
FROM tb_shop
WHERE images LIKE '/imgs/shop/nanjing/%'
ORDER BY id;
