package com.macro.mall.service.impl;

import com.macro.mall.common.api.CouponUseStatus;
import com.macro.mall.dao.SmsCouponDao;
import com.macro.mall.dao.SmsCouponProductCategoryRelationDao;
import com.macro.mall.dao.SmsCouponProductRelationDao;
import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.mapper.SmsCouponProductCategoryRelationMapper;
import com.macro.mall.mapper.SmsCouponProductRelationMapper;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponExample;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Admin coupon service unit tests.
 * Covers: create (status init), issue (CAS optimistic lock)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmsCouponServiceImplTest {

    @Mock private SmsCouponMapper couponMapper;
    @Mock private SmsCouponDao couponDao;
    @Mock private SmsCouponProductRelationDao productRelationDao;
    @Mock private SmsCouponProductCategoryRelationDao productCategoryRelationDao;
    @Mock private SmsCouponProductRelationMapper productRelationMapper;
    @Mock private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;

    @InjectMocks
    private SmsCouponServiceImpl couponService;

    private SmsCoupon createTestCoupon() {
        SmsCoupon c = new SmsCoupon();
        c.setId(1L);
        c.setName("test-coupon");
        c.setType(0);
        c.setAmount(new BigDecimal("30.00"));
        c.setMinPoint(new BigDecimal("100.00"));
        c.setPerLimit(1);
        c.setPlatform(0);
        c.setPublishCount(100);
        c.setUseType(0);
        c.setStartTime(new Date(System.currentTimeMillis() - 86400000L));
        c.setEndTime(new Date(System.currentTimeMillis() + 86400000L * 7));
        c.setStatus(CouponUseStatus.TEMPLATE_CREATED);
        c.setVersion(0);
        return c;
    }

    @Nested
    class CreateTests {
        @Test
        void testCreate_InitialStatus() {
            SmsCouponParam param = new SmsCouponParam();
            param.setName("test-coupon");
            param.setType(0);
            param.setAmount(new BigDecimal("30.00"));
            param.setMinPoint(new BigDecimal("100.00"));
            param.setPerLimit(1);
            param.setPlatform(0);
            param.setPublishCount(100);
            param.setUseType(0);
            when(couponMapper.insert(any(SmsCoupon.class))).thenAnswer(inv -> {
                SmsCoupon c = inv.getArgument(0);
                c.setId(1L);
                return 1;
            });
            int count = couponService.create(param);
            assertEquals(1, count);
            ArgumentCaptor<SmsCoupon> captor = ArgumentCaptor.forClass(SmsCoupon.class);
            verify(couponMapper).insert(captor.capture());
            assertEquals(Integer.valueOf(CouponUseStatus.TEMPLATE_CREATED), captor.getValue().getStatus());
            assertEquals(Integer.valueOf(0), captor.getValue().getVersion());
        }
    }

    @Nested
    class IssueTests {
        @Test
        void testIssue_Success() {
            SmsCoupon coupon = createTestCoupon();
            coupon.setStatus(CouponUseStatus.TEMPLATE_CREATED);
            coupon.setVersion(0);
            when(couponMapper.selectByPrimaryKey(1L)).thenReturn(coupon);
            when(couponMapper.updateByExampleSelective(any(), any())).thenReturn(1);
            int count = couponService.issueCoupon(1L);
            assertEquals(1, count);
            ArgumentCaptor<SmsCoupon> captor = ArgumentCaptor.forClass(SmsCoupon.class);
            verify(couponMapper).updateByExampleSelective(captor.capture(), any(SmsCouponExample.class));
            assertEquals(Integer.valueOf(CouponUseStatus.TEMPLATE_ISSUED), captor.getValue().getStatus());
        }

        @Test
        void testIssue_AlreadyIssued() {
            SmsCoupon coupon = createTestCoupon();
            coupon.setStatus(CouponUseStatus.TEMPLATE_ISSUED);
            when(couponMapper.selectByPrimaryKey(1L)).thenReturn(coupon);
            assertEquals(0, couponService.issueCoupon(1L));
        }

        @Test
        void testIssue_NullCoupon() {
            when(couponMapper.selectByPrimaryKey(999L)).thenReturn(null);
            assertEquals(0, couponService.issueCoupon(999L));
        }
    }

    @Test
    void testConstants() {
        assertEquals(0, CouponUseStatus.TEMPLATE_CREATED);
        assertEquals(1, CouponUseStatus.TEMPLATE_ISSUED);
        assertEquals(0, CouponUseStatus.CREATED);
        assertEquals(1, CouponUseStatus.ISSUED);
        assertEquals(2, CouponUseStatus.USED);
        assertEquals(3, CouponUseStatus.EXPIRED);
    }
}
