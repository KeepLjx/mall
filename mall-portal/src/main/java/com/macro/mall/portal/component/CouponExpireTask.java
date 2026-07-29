package com.macro.mall.portal.component;

import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Scheduled task to expire overdue coupons.
 * Runs hourly, marks issued-but-expired coupons as expired.
 * Created by CodeBuddy on 2026/7/29.
 */
@Component
public class CouponExpireTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(CouponExpireTask.class);

    @Autowired
    private SmsCouponHistoryDao couponHistoryDao;

    /**
     * Expire coupons whose end_time has passed.
     * Updates use_status from 1 (ISSUED) to 3 (EXPIRED).
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void expireOverdueCoupons() {
        try {
            int count = couponHistoryDao.expireOverdueCoupons(new Date());
            if (count > 0) {
                LOGGER.info("Coupon expiration processed: {} records", count);
            }
        } catch (Exception e) {
            LOGGER.error("Coupon expiration processing error", e);
        }
    }
}
