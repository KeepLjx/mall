package com.macro.mall.common.api;

/**
 * Coupon four-state enum.
 * States: Created -> Issued -> Used/Expired
 */
public final class CouponUseStatus {

    private CouponUseStatus() {}

    /** Created / claimed, pending activation */
    public static final int CREATED = 0;
    /** Issued / available for use */
    public static final int ISSUED = 1;
    /** Used / written off */
    public static final int USED = 2;
    /** Expired */
    public static final int EXPIRED = 3;

    /** Coupon template status: created (not yet issued) */
    public static final int TEMPLATE_CREATED = 0;
    /** Coupon template status: issued */
    public static final int TEMPLATE_ISSUED = 1;

    /**
     * Check if coupon is in available state.
     */
    public static boolean isAvailable(int useStatus) {
        return useStatus == ISSUED;
    }

    /**
     * Get status description.
     */
    public static String getDesc(int useStatus) {
        switch (useStatus) {
            case CREATED: return "Created";
            case ISSUED:  return "Issued";
            case USED:    return "Used";
            case EXPIRED: return "Expired";
            default:      return "Unknown";
        }
    }
}
