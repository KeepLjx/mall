package com.macro.mall.portal.service.impl;

import com.macro.mall.common.domain.CouponConstant;
import com.macro.mall.common.domain.CouponStatus;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.SmsCouponHistoryExample;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.portal.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Coupon core service: state machine + CAS concurrent write-off
 */
@Service
public class CouponServiceImpl implements CouponService {

    private static final Logger LOG = LoggerFactory.getLogger(CouponServiceImpl.class);

    @Autowired
    private SmsCouponHistoryMapper couponHistoryMapper;

    @Override
    public boolean transitionStatus(SmsCouponHistory couponHistory, CouponStatus targetStatus) {
        if (couponHistory == null) {
            throw new IllegalArgumentException("Coupon history record cannot be null");
        }
        if (targetStatus == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }

        Integer currentStatusCode = couponHistory.getStatus();
        if (currentStatusCode == null) {
            currentStatusCode = CouponStatus.DISTRIBUTED.getCode();
        }

        CouponStatus currentStatus = CouponStatus.fromCode(currentStatusCode);

        if (!currentStatus.canTransitionTo(targetStatus)) {
            String msg = String.format("Illegal coupon status transition: %s -> %s (couponHistoryId=%d)",
                    currentStatus.getDesc(), targetStatus.getDesc(), couponHistory.getId());
            LOG.warn(msg);
            throw new IllegalStateException(msg);
        }

        couponHistory.setStatus(targetStatus.getCode());
        couponHistoryMapper.updateByPrimaryKeySelective(couponHistory);
        LOG.info("Coupon status transition success: {} -> {} (couponHistoryId={})",
                currentStatus.getDesc(), targetStatus.getDesc(), couponHistory.getId());
        return true;
    }

    @Override
    @Transactional
    public boolean useCouponCas(Long couponHistoryId, Long orderId, String orderSn) {
        if (couponHistoryId == null) {
            Asserts.fail("Coupon history ID cannot be null");
        }

        // 1. Query coupon record to get current version
        SmsCouponHistory couponHistory = couponHistoryMapper.selectByPrimaryKey(couponHistoryId);
        if (couponHistory == null) {
            Asserts.fail("Coupon history record not found");
        }

        // 2. Check status: must be DISTRIBUTED and unused
        if (couponHistory.getStatus() == null || 
            couponHistory.getStatus() != CouponStatus.DISTRIBUTED.getCode()) {
            LOG.warn("Coupon status mismatch, cannot write-off: couponHistoryId={}, status={}",
                    couponHistoryId, couponHistory.getStatus());
            return false;
        }
        if (couponHistory.getUseStatus() == null || 
            couponHistory.getUseStatus() != CouponConstant.USE_STATUS_UNUSED) {
            LOG.warn("Coupon already used or expired: couponHistoryId={}, useStatus={}",
                    couponHistoryId, couponHistory.getUseStatus());
            return false;
        }

        // 3. CAS atomic update: WHERE id=? AND version=? AND use_status=0 AND status=1
        int currentVersion = couponHistory.getVersion() == null ? 0 : couponHistory.getVersion();

        SmsCouponHistory updateRecord = new SmsCouponHistory();
        updateRecord.setId(couponHistoryId);
        updateRecord.setUseStatus(CouponConstant.USE_STATUS_USED);
        updateRecord.setStatus(CouponStatus.USED.getCode());
        updateRecord.setUseTime(new Date());
        updateRecord.setOrderId(orderId);
        updateRecord.setOrderSn(orderSn);
        updateRecord.setVersion(currentVersion + 1);

        SmsCouponHistoryExample example = new SmsCouponHistoryExample();
        example.createCriteria()
                .andIdEqualTo(couponHistoryId)
                .andVersionEqualTo(currentVersion)
                .andUseStatusEqualTo(CouponConstant.USE_STATUS_UNUSED)
                .andStatusEqualTo(CouponStatus.DISTRIBUTED.getCode());

        int updated = couponHistoryMapper.updateByExampleSelective(updateRecord, example);

        if (updated == 0) {
            LOG.warn("Coupon write-off CAS conflict: couponHistoryId={}, version={}", couponHistoryId, currentVersion);
            return false;
        }

        LOG.info("Coupon write-off success: couponHistoryId={}, orderId={}, orderSn={}",
                couponHistoryId, orderId, orderSn);
        return true;
    }

    @Override
    public void markAsDistributed(SmsCouponHistory couponHistory) {
        couponHistory.setStatus(CouponStatus.DISTRIBUTED.getCode());
        couponHistory.setUseStatus(CouponConstant.USE_STATUS_UNUSED);
    }

    @Override
    public int markExpired(Long couponHistoryId) {
        SmsCouponHistory couponHistory = couponHistoryMapper.selectByPrimaryKey(couponHistoryId);
        if (couponHistory == null) {
            return 0;
        }
        
        if (couponHistory.getUseStatus() != null && 
            couponHistory.getUseStatus() == CouponConstant.USE_STATUS_UNUSED) {
            try {
                transitionStatus(couponHistory, CouponStatus.EXPIRED);
            } catch (IllegalStateException e) {
                LOG.warn("Coupon expire transition failed: couponHistoryId={}", couponHistoryId, e);
                return 0;
            }
            couponHistory.setUseStatus(CouponConstant.USE_STATUS_EXPIRED);
            return couponHistoryMapper.updateByPrimaryKeySelective(couponHistory);
        }
        return 0;
    }
}
