package com.macro.mall.common.domain;

/**
 * 优惠券生命周期状态枚举
 * CREATED(0) -> DISTRIBUTED(1) -> USED(2)/EXPIRED(3)
 */
public enum CouponStatus {

    /**
     * 已创建：优惠券模板已创建，尚未发放给用户
     */
    CREATED(0, "已创建"),

    /**
     * 已发放：优惠券已发放给用户，待使用
     */
    DISTRIBUTED(1, "已发放"),

    /**
     * 已核销：优惠券已在下单时使用
     */
    USED(2, "已核销"),

    /**
     * 已过期：优惠券超过有效期自动/手动过期
     */
    EXPIRED(3, "已过期");

    private final int code;
    private final String desc;

    CouponStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据code获取枚举
     */
    public static CouponStatus fromCode(int code) {
        for (CouponStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid coupon status code: " + code);
    }

    /**
     * 判断是否允许从当前状态转移到目标状态
     */
    public boolean canTransitionTo(CouponStatus target) {
        switch (this) {
            case CREATED:
                return target == DISTRIBUTED;
            case DISTRIBUTED:
                return target == USED || target == EXPIRED;
            case USED:
            case EXPIRED:
                return false;
            default:
                return false;
        }
    }
}
