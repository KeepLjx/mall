-- =============================================
-- 优惠券系统升级：四状态流转 + 乐观锁并发核销
-- 执行前请备份数据库
-- =============================================

-- 1. sms_coupon 表：新增优惠券状态 + 乐观锁版本号
ALTER TABLE sms_coupon
    ADD COLUMN `status` INT NOT NULL DEFAULT 0 COMMENT '优惠券状态: 0->创建(未发放); 1->已发放',
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';

-- 已有优惠券默认设置为已发放状态
UPDATE sms_coupon SET `status` = 1 WHERE `status` = 0 AND publish_count > 0;

-- 2. sms_coupon_history 表：新增乐观锁版本号
ALTER TABLE sms_coupon_history
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';

-- 3. 更新 use_status 注释为四状态流转
ALTER TABLE sms_coupon_history
    MODIFY COLUMN `use_status` INT NOT NULL DEFAULT 0 COMMENT '优惠券使用状态: 0->已创建/已领取; 1->已发放/可使用; 2->已核销/已使用; 3->已过期';

-- 4. 为核销操作添加索引（优化 CAS 更新性能）
ALTER TABLE sms_coupon_history
    ADD INDEX `idx_member_coupon_status` (`member_id`, `coupon_id`, `use_status`);
