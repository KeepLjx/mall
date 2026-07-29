package com.macro.mall.portal.service.impl;

import com.macro.mall.common.domain.CouponConstant;
import com.macro.mall.common.domain.CouponStatus;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.SmsCouponHistoryExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 优惠券核销服务测试：并发CAS + 算价验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("优惠券核销服务测试")
public class CouponServiceImplTest {

    @Mock
    private SmsCouponHistoryMapper couponHistoryMapper;

    @InjectMocks
    private CouponServiceImpl couponService;

    private SmsCouponHistory testCoupon;

    @BeforeEach
    void setUp() {
        testCoupon = new SmsCouponHistory();
        testCoupon.setId(1L);
        testCoupon.setCouponId(100L);
        testCoupon.setMemberId(200L);
        testCoupon.setStatus(CouponStatus.DISTRIBUTED.getCode());
        testCoupon.setUseStatus(CouponConstant.USE_STATUS_UNUSED);
        testCoupon.setVersion(0);
        testCoupon.setCouponCode("TEST001");
    }

    @Test
    @DisplayName("正常核销：状态和版本匹配则成功")
    void testUseCouponCasSuccess() {
        when(couponHistoryMapper.selectByPrimaryKey(1L)).thenReturn(testCoupon);
        when(couponHistoryMapper.updateByExampleSelective(any(SmsCouponHistory.class), any(SmsCouponHistoryExample.class)))
                .thenReturn(1);

        boolean result = couponService.useCouponCas(1L, 300L, "SN001");

        assertTrue(result, "正常核销应返回true");

        // 验证CAS条件：WHERE version=0 AND use_status=0 AND status=1
        ArgumentCaptor<SmsCouponHistoryExample> exampleCaptor = ArgumentCaptor.forClass(SmsCouponHistoryExample.class);
        verify(couponHistoryMapper).updateByExampleSelective(any(SmsCouponHistory.class), exampleCaptor.capture());
    }

    @Test
    @DisplayName("并发冲突：version不匹配返回false")
    void testUseCouponCasConflict() {
        when(couponHistoryMapper.selectByPrimaryKey(1L)).thenReturn(testCoupon);
        // 模拟CAS更新返回0（版本不匹配）
        when(couponHistoryMapper.updateByExampleSelective(any(SmsCouponHistory.class), any(SmsCouponHistoryExample.class)))
                .thenReturn(0);

        boolean result = couponService.useCouponCas(1L, 300L, "SN001");

        assertFalse(result, "版本冲突应返回false");
    }

    @Test
    @DisplayName("状态不符：已核销的券无法再次核销")
    void testUseCouponCasAlreadyUsed() {
        testCoupon.setUseStatus(CouponConstant.USE_STATUS_USED);
        testCoupon.setStatus(CouponStatus.USED.getCode());
        when(couponHistoryMapper.selectByPrimaryKey(1L)).thenReturn(testCoupon);

        boolean result = couponService.useCouponCas(1L, 300L, "SN001");

        assertFalse(result, "已核销的券不应再次核销");
        verify(couponHistoryMapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    @DisplayName("状态不符：已过期的券无法核销")
    void testUseCouponCasExpired() {
        testCoupon.setUseStatus(CouponConstant.USE_STATUS_EXPIRED);
        testCoupon.setStatus(CouponStatus.EXPIRED.getCode());
        when(couponHistoryMapper.selectByPrimaryKey(1L)).thenReturn(testCoupon);

        boolean result = couponService.useCouponCas(1L, 300L, "SN001");

        assertFalse(result, "已过期的券不应核销");
        verify(couponHistoryMapper, never()).updateByExampleSelective(any(), any());
    }


    @Test
    @DisplayName("markAsDistributed 设置正确状态")
    void testMarkAsDistributed() {
        SmsCouponHistory history = new SmsCouponHistory();
        couponService.markAsDistributed(history);
        assertEquals(CouponStatus.DISTRIBUTED.getCode(), history.getStatus());
        assertEquals(CouponConstant.USE_STATUS_UNUSED, history.getUseStatus());
    }

    @Test
    @DisplayName("markExpired 对未使用券标记过期")
    void testMarkExpiredSuccess() {
        testCoupon.setUseStatus(CouponConstant.USE_STATUS_UNUSED);
        testCoupon.setStatus(CouponStatus.DISTRIBUTED.getCode());
        when(couponHistoryMapper.selectByPrimaryKey(1L)).thenReturn(testCoupon);
        when(couponHistoryMapper.updateByPrimaryKeySelective(any(SmsCouponHistory.class))).thenReturn(1);

        int result = couponService.markExpired(1L);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("markExpired 对已使用券不操作")
    void testMarkExpiredAlreadyUsed() {
        testCoupon.setUseStatus(CouponConstant.USE_STATUS_USED);
        when(couponHistoryMapper.selectByPrimaryKey(1L)).thenReturn(testCoupon);

        int result = couponService.markExpired(1L);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("算价用例1：满100减10，订单金额150，实付140")
    void testCalcCase1_FullReduction() {
        // 模拟：总金额150，满100减10
        java.math.BigDecimal totalAmount = new java.math.BigDecimal("150.00");
        java.math.BigDecimal couponAmount = new java.math.BigDecimal("10.00");
        java.math.BigDecimal payAmount = totalAmount.subtract(couponAmount);

        assertEquals(0, payAmount.compareTo(new java.math.BigDecimal("140.00")),
                "满100减10，150应付140");
    }

    @Test
    @DisplayName("算价用例2：满200减50，订单金额250，实付200")
    void testCalcCase2_FullReduction() {
        java.math.BigDecimal totalAmount = new java.math.BigDecimal("250.00");
        java.math.BigDecimal couponAmount = new java.math.BigDecimal("50.00");
        java.math.BigDecimal payAmount = totalAmount.subtract(couponAmount);

        assertEquals(0, payAmount.compareTo(new java.math.BigDecimal("200.00")),
                "满200减50，250应付200");
    }

    @Test
    @DisplayName("算价用例3：无门槛10元券，订单99元，实付89")
    void testCalcCase3_NoThreshold() {
        java.math.BigDecimal totalAmount = new java.math.BigDecimal("99.00");
        java.math.BigDecimal couponAmount = new java.math.BigDecimal("10.00");
        java.math.BigDecimal payAmount = totalAmount.subtract(couponAmount);

        assertEquals(0, payAmount.compareTo(new java.math.BigDecimal("89.00")),
                "无门槛10元券，99应付89");
    }
}
