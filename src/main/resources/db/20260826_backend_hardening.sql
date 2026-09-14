-- Run only after verifying the duplicate query returns no rows.
SELECT user_id, follow_user_id, COUNT(*) AS duplicate_count
FROM tb_follow GROUP BY user_id, follow_user_id HAVING COUNT(*) > 1;

ALTER TABLE tb_follow
  ADD UNIQUE KEY uk_follow_user_target (user_id, follow_user_id);
