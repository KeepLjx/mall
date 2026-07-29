package com.macro.mall.portal.service.impl;

import com.macro.mall.common.domain.CouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优惠券状态机单元测试
 * 覆盖四态流转：CREATED -> DISTRIBUTED -> USED/EXPIRED
 */
@DisplayName("优惠券状态流转测试")
public class CouponStatusTest {

    @Test
    @DisplayName("CREATED -> DISTRIBUTED 合法流转")
    void testCreatedToDistributed() {
        assertTrue(CouponStatus.CREATED.canTransitionTo(CouponStatus.DISTRIBUTED));
    }

    @Test
    @DisplayName("DISTRIBUTED -> USED 合法流转")
    void testDistributedToUsed() {
        assertTrue(CouponStatus.DISTRIBUTED.canTransitionTo(CouponStatus.USED));
    }

    @Test
    @DisplayName("DISTRIBUTED -> EXPIRED 合法流转")
    void testDistributedToExpired() {
        assertTrue(CouponStatus.DISTRIBUTED.canTransitionTo(CouponStatus.EXPIRED));
    }

    @ParameterizedTest(name = "非法流转: {0} -> {1}")
    @MethodSource("illegalTransitionsProvider")
    @DisplayName("非法状态流转应被禁止")
    void testIllegalTransitions(CouponStatus from, CouponStatus to) {
        assertFalse(from.canTransitionTo(to),
                String.format("状态 %s 不应该能流转到 %s", from.getDesc(), to.getDesc()));
    }

    static Stream<Arguments> illegalTransitionsProvider() {
        return Stream.of(
                // CREATED 只能到 DISTRIBUTED
                Arguments.of(CouponStatus.CREATED, CouponStatus.USED),
                Arguments.of(CouponStatus.CREATED, CouponStatus.EXPIRED),
                Arguments.of(CouponStatus.CREATED, CouponStatus.CREATED),
                // DISTRIBUTED 只能到 USED 或 EXPIRED
                Arguments.of(CouponStatus.DISTRIBUTED, CouponStatus.CREATED),
                Arguments.of(CouponStatus.DISTRIBUTED, CouponStatus.DISTRIBUTED),
                // USED 是终态
                Arguments.of(CouponStatus.USED, CouponStatus.CREATED),
                Arguments.of(CouponStatus.USED, CouponStatus.DISTRIBUTED),
                Arguments.of(CouponStatus.USED, CouponStatus.USED),
                Arguments.of(CouponStatus.USED, CouponStatus.EXPIRED),
                // EXPIRED 是终态
                Arguments.of(CouponStatus.EXPIRED, CouponStatus.CREATED),
                Arguments.of(CouponStatus.EXPIRED, CouponStatus.DISTRIBUTED),
                Arguments.of(CouponStatus.EXPIRED, CouponStatus.USED),
                Arguments.of(CouponStatus.EXPIRED, CouponStatus.EXPIRED)
        );
    }

    @Test
    @DisplayName("fromCode 正确解析状态码")
    void testFromCode() {
        assertEquals(CouponStatus.CREATED, CouponStatus.fromCode(0));
        assertEquals(CouponStatus.DISTRIBUTED, CouponStatus.fromCode(1));
        assertEquals(CouponStatus.USED, CouponStatus.fromCode(2));
        assertEquals(CouponStatus.EXPIRED, CouponStatus.fromCode(3));
    }

    @Test
    @DisplayName("fromCode 无效状态码抛异常")
    void testFromCodeInvalid() {
        assertThrows(IllegalArgumentException.class, () -> CouponStatus.fromCode(99));
        assertThrows(IllegalArgumentException.class, () -> CouponStatus.fromCode(-1));
    }

    @Test
    @DisplayName("状态码和描述一一对应")
    void testCodeAndDesc() {
        assertEquals(0, CouponStatus.CREATED.getCode());
        assertEquals("已创建", CouponStatus.CREATED.getDesc());
        assertEquals(1, CouponStatus.DISTRIBUTED.getCode());
        assertEquals("已发放", CouponStatus.DISTRIBUTED.getDesc());
        assertEquals(2, CouponStatus.USED.getCode());
        assertEquals("已核销", CouponStatus.USED.getDesc());
        assertEquals(3, CouponStatus.EXPIRED.getCode());
        assertEquals("已过期", CouponStatus.EXPIRED.getDesc());
    }
}
