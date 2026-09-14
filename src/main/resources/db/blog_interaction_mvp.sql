-- Apply once to an existing SmartLife / hmdp database.
ALTER TABLE `tb_blog`
  ADD COLUMN `visibility` tinyint(1) UNSIGNED NOT NULL DEFAULT 0
  COMMENT '可见性，0：公开，1：仅自己可见'
  AFTER `comments`;

CREATE TABLE IF NOT EXISTS `tb_blog_collection` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '笔记id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_blog_collection_user_blog` (`user_id`, `blog_id`) USING BTREE,
  KEY `idx_blog_collection_blog` (`blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='笔记收藏';
