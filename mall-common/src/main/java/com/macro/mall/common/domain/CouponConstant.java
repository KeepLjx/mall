package com.macro.mall.common.domain;

/**
 * 优惠券相关常量
 */
public final class CouponConstant {

    private CouponConstant() {
        // 工具类禁止实例化
    }

    /** 优惠券历史记录 useStatus: 未使用（对应 DISTRIBUTED 状态） */
    public static final int USE_STATUS_UNUSED = 0;

    /** 优惠券历史记录 useStatus: 已使用（对应 USED 状态） */
    public static final int USE_STATUS_USED = 1;

    /** 优惠券历史记录 useStatus: 已过期（对应 EXPIRED 状态） */
    public static final int USE_STATUS_EXPIRED = 2;

    /** 获取类型：后台赠送 */
    public static final int GET_TYPE_ADMIN = 0;

    /** 获取类型：主动获取 */
    public static final int GET_TYPE_MEMBER = 1;

    /** Redis 分布式锁 key 前缀：优惠券核销 */
    public static final String REDIS_KEY_COUPON_LOCK = "coupon:lock:";

    /** 优惠券核销锁超时时间（毫秒） */
    public static final long COUPON_LOCK_TIMEOUT_MS = 3000;
}
