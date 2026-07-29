package com.macro.mall.portal.service;

import com.macro.mall.common.domain.CouponStatus;
import com.macro.mall.model.SmsCouponHistory;

/**
 * Coupon core service: state machine + concurrent write-off
 */
public interface CouponService {

    /**
     * Validate and execute state transition
     * @param couponHistory coupon record with current status
     * @param targetStatus  target status
     * @return true if transition succeeded
     * @throws IllegalStateException if transition is illegal
     */
    boolean transitionStatus(SmsCouponHistory couponHistory, CouponStatus targetStatus);

    /**
     * Concurrent-safe coupon write-off using optimistic lock (CAS)
     * Atomic update: UPDATE sms_coupon_history SET use_status=1, status=2, version=version+1
     * WHERE id=? AND version=? AND use_status=0 AND status=1
     * 
     * @param couponHistoryId coupon history record ID
     * @param orderId         order ID
     * @param orderSn         order serial number
     * @return true if write-off succeeded, false if concurrent conflict
     */
    boolean useCouponCas(Long couponHistoryId, Long orderId, String orderSn);

    /**
     * Mark coupon as DISTRIBUTED when issuing
     */
    void markAsDistributed(SmsCouponHistory couponHistory);

    /**
     * Mark coupon as expired
     * @return number of updated records
     */
    int markExpired(Long couponHistoryId);
}
