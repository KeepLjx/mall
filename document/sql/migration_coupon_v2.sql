-- =====================================================
-- 优惠券系统 v2 迁移脚本
-- 新增功能：四状态流转 + 并发核销乐观锁
-- 执行前请备份数据库！
-- =====================================================

-- 1. sms_coupon_history 表新增 version 列（乐观锁）
ALTER TABLE `sms_coupon_history`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `order_sn`;

-- 2. sms_coupon_history 表新增 status 列（四状态生命周期）
--    0: CREATED(已创建), 1: DISTRIBUTED(已发放), 2: USED(已核销), 3: EXPIRED(已过期)
ALTER TABLE `sms_coupon_history`
    ADD COLUMN `status` INT NOT NULL DEFAULT 0 COMMENT '优惠券状态：0->已创建；1->已发放；2->已核销；3->已过期' AFTER `version`;

-- 3. 为已存在的记录回填 status 值（根据 use_status 映射）
--    use_status=0 → status=1 (已发放)
--    use_status=1 → status=2 (已核销)
--    use_status=2 → status=3 (已过期)
UPDATE `sms_coupon_history` SET `status` = 1 WHERE `use_status` = 0;
UPDATE `sms_coupon_history` SET `status` = 2 WHERE `use_status` = 1;
UPDATE `sms_coupon_history` SET `status` = 3 WHERE `use_status` = 2;

-- 4. 为 sms_coupon_history 表添加索引（支持按状态+会员查询）
ALTER TABLE `sms_coupon_history`
    ADD INDEX `idx_member_status` (`member_id`, `status`) USING BTREE;

-- 5. sms_coupon 表新增 status 列（优惠券模板状态）
ALTER TABLE `sms_coupon`
    ADD COLUMN `status` INT NOT NULL DEFAULT 0 COMMENT '优惠券状态：0->已创建；1->已发放；2->已核销；3->已过期' AFTER `member_level`;

-- 6. 为已存在的优惠券模板设置默认状态
UPDATE `sms_coupon` SET `status` = 0;

-- =====================================================
-- 迁移完成，请验证数据完整性
-- SELECT COUNT(*) FROM sms_coupon_history WHERE status NOT IN (0,1,2,3);
-- SELECT COUNT(*) FROM sms_coupon WHERE status != 0;
-- =====================================================
