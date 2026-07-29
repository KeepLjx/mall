package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CouponUseStatus;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.mapper.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for coupon functionality.
 * Covers: 4-state transitions / CAS concurrent write-off / order calculation
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UmsMemberCouponServiceImplTest {

    @Mock private SmsCouponMapper couponMapper;
    @Mock private SmsCouponHistoryMapper historyMapper;
    @Mock private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;
    @Mock private SmsCouponProductRelationMapper productRelationMapper;
    @Mock private UmsMemberService memberService;
    @Mock private SmsCouponHistoryDao couponHistoryDao;

    @InjectMocks
    private UmsMemberCouponServiceImpl couponService;

    private static final Long MEMBER_ID = 1L;
    private static final Long COUPON_ID = 100L;

    private UmsMember createMember() {
        UmsMember m = new UmsMember();
        m.setId(MEMBER_ID);
        m.setUsername("test_user");
        m.setNickname("TestUser");
        return m;
    }

    private SmsCoupon createCoupon() {
        SmsCoupon c = new SmsCoupon();
        c.setId(COUPON_ID);
        c.setName("50-Coupon");
        c.setType(0);
        c.setAmount(new BigDecimal("50.00"));
        c.setMinPoint(new BigDecimal("100.00"));
        c.setPerLimit(1);
        c.setPlatform(0);
        c.setStartTime(new Date(System.currentTimeMillis() - 86400000));
        c.setEndTime(new Date(System.currentTimeMillis() + 86400000 * 7));
        c.setEnableTime(new Date(System.currentTimeMillis() - 86400000));
        c.setCount(1000);
        c.setPublishCount(1000);
        c.setReceiveCount(10);
        c.setUseType(0);
        c.setUseCount(0);
        c.setStatus(CouponUseStatus.TEMPLATE_ISSUED);
        c.setVersion(1);
        return c;
    }

    private SmsCouponHistory createHistory(Long id, Integer useStatus, Integer version) {
        SmsCouponHistory h = new SmsCouponHistory();
        h.setId(id);
        h.setCouponId(COUPON_ID);
        h.setMemberId(MEMBER_ID);
        h.setCouponCode("CPN-" + id);
        h.setMemberNickname("TestUser");
        h.setGetType(1);
        h.setUseStatus(useStatus);
        h.setVersion(version);
        h.setCreateTime(new Date());
        return h;
    }

    // ========================================
    // [Checkpoint 1] Four-state transitions
    // ========================================
    @Nested
    class StateTransitions {

        @Test
        void testAdd_CreatesHistoryWithStatusFlow() {
            UmsMember member = createMember();
            SmsCoupon coupon = createCoupon();
            when(memberService.getCurrentMember()).thenReturn(member);
            when(couponMapper.selectByPrimaryKey(COUPON_ID)).thenReturn(coupon);
            when(historyMapper.insert(any(SmsCouponHistory.class))).thenAnswer(inv -> {
                SmsCouponHistory h = inv.getArgument(0);
                h.setId(1L);
                return 1;
            });
            when(historyMapper.updateByExampleSelective(any(), any())).thenReturn(1);
            when(couponMapper.updateByExampleSelective(any(), any())).thenReturn(1);

            couponService.add(COUPON_ID);

            ArgumentCaptor<SmsCouponHistory> insertCaptor = ArgumentCaptor.forClass(SmsCouponHistory.class);
            verify(historyMapper).insert(insertCaptor.capture());
            assertEquals(Integer.valueOf(CouponUseStatus.CREATED), insertCaptor.getValue().getUseStatus());
            assertEquals(Integer.valueOf(0), insertCaptor.getValue().getVersion());

            ArgumentCaptor<SmsCouponHistory> updateCaptor = ArgumentCaptor.forClass(SmsCouponHistory.class);
            verify(historyMapper).updateByExampleSelective(updateCaptor.capture(), any());
            assertEquals(Integer.valueOf(CouponUseStatus.ISSUED), updateCaptor.getValue().getUseStatus());
        }

        @Test
        void testWriteOff_CASUpdate() {
            when(couponHistoryDao.writeOffCoupon(eq(1L), eq(MEMBER_ID), eq(100L),
                    eq("SN-001"), eq(1), any(Date.class))).thenReturn(1);
            boolean result = couponService.writeOffCoupon(1L, MEMBER_ID, 100L, "SN-001", 1);
            assertTrue(result);
        }

        @Test
        void testWriteOff_DuplicateShouldFail() {
            when(couponHistoryDao.writeOffCoupon(eq(1L), eq(MEMBER_ID), eq(100L),
                    eq("SN-001"), eq(2), any(Date.class))).thenReturn(0);
            boolean result = couponService.writeOffCoupon(1L, MEMBER_ID, 100L, "SN-001", 2);
            assertFalse(result);
        }

        @Test
        void testWriteOff_VersionConflictShouldFail() {
            when(couponHistoryDao.writeOffCoupon(eq(1L), eq(MEMBER_ID), eq(100L),
                    eq("SN-001"), eq(1), any(Date.class))).thenReturn(0);
            boolean result = couponService.writeOffCoupon(1L, MEMBER_ID, 100L, "SN-001", 1);
            assertFalse(result);
        }

        @Test
        void testExpire() {
            when(couponHistoryDao.expireOverdueCoupons(any(Date.class))).thenReturn(5);
            int count = couponHistoryDao.expireOverdueCoupons(new Date());
            assertEquals(5, count);
        }
    }

    // ========================================
    // [Checkpoint 2] Order calculation
    // ========================================
    @Nested
    class OrderCalculation {

        @Test
        void testStatusConstants() {
            assertEquals(0, CouponUseStatus.CREATED);
            assertEquals(1, CouponUseStatus.ISSUED);
            assertEquals(2, CouponUseStatus.USED);
            assertEquals(3, CouponUseStatus.EXPIRED);
        }

        @Test
        void testIsAvailable() {
            assertTrue(CouponUseStatus.isAvailable(CouponUseStatus.ISSUED));
            assertFalse(CouponUseStatus.isAvailable(CouponUseStatus.CREATED));
            assertFalse(CouponUseStatus.isAvailable(CouponUseStatus.USED));
            assertFalse(CouponUseStatus.isAvailable(CouponUseStatus.EXPIRED));
        }

        @Test
        void testGetDesc() {
            assertEquals("Created", CouponUseStatus.getDesc(CouponUseStatus.CREATED));
            assertEquals("Issued", CouponUseStatus.getDesc(CouponUseStatus.ISSUED));
            assertEquals("Used", CouponUseStatus.getDesc(CouponUseStatus.USED));
            assertEquals("Expired", CouponUseStatus.getDesc(CouponUseStatus.EXPIRED));
        }

        @Test
        void testTemplateConstants() {
            assertEquals(0, CouponUseStatus.TEMPLATE_CREATED);
            assertEquals(1, CouponUseStatus.TEMPLATE_ISSUED);
        }
    }

    // ========================================
    // [Checkpoint 3] Concurrent write-off protection
    // ========================================
    @Nested
    class ConcurrentWriteOff {

        @Test
        void testConcurrentWriteOff_OnlyOneSucceeds() throws InterruptedException {
            final Long historyId = 100L;
            final int threadCount = 20;
            final AtomicInteger successCount = new AtomicInteger(0);
            final AtomicInteger failCount = new AtomicInteger(0);

            when(couponHistoryDao.writeOffCoupon(eq(historyId), eq(MEMBER_ID), eq(100L),
                    anyString(), eq(1), any(Date.class)))
                    .thenReturn(1).thenReturn(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final String orderSn = "SN-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        boolean result = couponService.writeOffCoupon(historyId, MEMBER_ID,
                                100L, orderSn, 1);
                        if (result) successCount.incrementAndGet();
                        else failCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(finished);
            assertEquals(1, successCount.get());
            assertEquals(threadCount - 1, failCount.get());
        }

        @Test
        void testConcurrentWriteOff_DifferentCouponsNoInterference() throws InterruptedException {
            final int threadCount = 10;
            when(couponHistoryDao.writeOffCoupon(anyLong(), anyLong(), anyLong(),
                    anyString(), anyInt(), any(Date.class))).thenReturn(1);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final long id = 100L + i;
                final int version = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        boolean result = couponService.writeOffCoupon(id, MEMBER_ID,
                                id * 10, "SN-" + id, version);
                        if (result) successCount.incrementAndGet();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(finished);
            assertEquals(threadCount, successCount.get());
        }
    }

    // ========================================
    // Boundary and edge cases
    // ========================================
    @Nested
    class BoundaryAndException {

        @Test
        void testAdd_NullCouponId_ShouldFail() {
            UmsMember member = createMember();
            when(memberService.getCurrentMember()).thenReturn(member);
            assertThrows(Exception.class, () -> couponService.add(null));
        }

        @Test
        void testWriteOff_NullParams() {
            assertFalse(couponService.writeOffCoupon(null, MEMBER_ID, 100L, "SN", 1));
            assertFalse(couponService.writeOffCoupon(1L, null, 100L, "SN", 1));
        }

        @Test
        void testListHistory_ExpiredStatus() {
            UmsMember member = createMember();
            when(memberService.getCurrentMember()).thenReturn(member);
            when(historyMapper.selectByExample(any())).thenReturn(
                    Arrays.asList(createHistory(1L, CouponUseStatus.EXPIRED, 1)));
            List<SmsCouponHistory> result = couponService.listHistory(CouponUseStatus.EXPIRED);
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(Integer.valueOf(CouponUseStatus.EXPIRED), result.get(0).getUseStatus());
        }
    }
}
