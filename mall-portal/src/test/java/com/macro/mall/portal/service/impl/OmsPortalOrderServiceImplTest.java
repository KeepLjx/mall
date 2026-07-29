package com.macro.mall.portal.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.mapper.UmsIntegrationConsumeSettingMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderItemExample;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.UmsIntegrationConsumeSetting;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.UmsMemberCouponService;
import com.macro.mall.portal.service.UmsMemberReceiveAddressService;
import com.macro.mall.portal.service.UmsMemberService;

/**
 * OmsPortalOrderServiceImpl 单元测试
 * 覆盖：正常流程、参数边界、异常分支、并发场�?
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPortalOrderServiceImpl 单元测试")
class OmsPortalOrderServiceImplTest {

    @Mock
    private UmsMemberService memberService;
    @Mock
    private OmsCartItemService cartItemService;
    @Mock
    private UmsMemberReceiveAddressService memberReceiveAddressService;
    @Mock
    private UmsMemberCouponService memberCouponService;
    @Mock
    private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;
    @Mock
    private PmsSkuStockMapper skuStockMapper;
    @Mock
    private SmsCouponHistoryDao couponHistoryDao;
    @Mock
    private OmsOrderMapper orderMapper;
    @Mock
    private PortalOrderItemDao orderItemDao;
    @Mock
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Mock
    private RedisService redisService;
    @Mock
    private PortalOrderDao portalOrderDao;
    @Mock
    private OmsOrderSettingMapper orderSettingMapper;
    @Mock
    private OmsOrderItemMapper orderItemMapper;
    @Mock
    private CancelOrderSender cancelOrderSender;

    @InjectMocks
    private OmsPortalOrderServiceImpl orderService;

    private UmsMember mockMember;

    @BeforeEach
    void setUp() {
        mockMember = new UmsMember();
        mockMember.setId(1L);
        mockMember.setUsername("testUser");
        mockMember.setIntegration(1000);

        // 注入 @Value 属�?
        ReflectionTestUtils.setField(orderService, "REDIS_KEY_ORDER_ID", "orderId");
        ReflectionTestUtils.setField(orderService, "REDIS_DATABASE", "mall");
    }

    // ============ 辅助方法：构建测试数�? ============

    private CartPromotionItem buildCartPromotionItem(Long id, Long productId, Long skuId,
                                                      BigDecimal price, Integer quantity,
                                                      BigDecimal reduceAmount, Integer realStock) {
        CartPromotionItem item = new CartPromotionItem();
        item.setId(id);
        item.setProductId(productId);
        item.setProductName("商品" + productId);
        item.setProductPic("pic.jpg");
        item.setProductAttr("颜色:�?");
        item.setProductBrand("品牌A");
        item.setProductSn("SN" + productId);
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setProductSkuId(skuId);
        item.setProductSkuCode("SKU" + skuId);
        item.setProductCategoryId(1L);
        item.setReduceAmount(reduceAmount);
        item.setPromotionMessage("满减促销");
        item.setIntegration(10);
        item.setGrowth(5);
        item.setRealStock(realStock);
        return item;
    }

    private UmsMemberReceiveAddress buildAddress(Long id) {
        UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
        address.setId(id);
        address.setName("张三");
        address.setPhoneNumber("13800138000");
        address.setPostCode("100000");
        address.setProvince("广东�?");
        address.setCity("深圳�?");
        address.setRegion("南山�?");
        address.setDetailAddress("科技园路1�?");
        return address;
    }

    private UmsIntegrationConsumeSetting buildIntegrationSetting() {
        UmsIntegrationConsumeSetting setting = new UmsIntegrationConsumeSetting();
        setting.setId(1L);
        setting.setCouponStatus(1); // 1=可与优惠券共�?
        setting.setUseUnit(100);
        setting.setMaxPercentPerOrder(50);
        return setting;
    }

    private CartPromotionItem buildCartPromotionItemWithRealStock(Long id, Long productId, Long skuId,
                                                                    BigDecimal price, Integer quantity,
                                                                    Integer realStock) {
        return buildCartPromotionItem(id, productId, skuId, price, quantity, BigDecimal.ZERO, realStock);
    }

    // ============ 第一部分：generateConfirmOrder ============

    @Nested
    @DisplayName("generateConfirmOrder - 正常流程")
    class GenerateConfirmOrder_Normal {

        @Test
        @DisplayName("正常流程：购物车有商品，用户有积分，应返回完整确认单")
        void shouldReturnCompleteConfirmOrderWhenCartHasItems() {
            // Given
            List<Long> cartIds = Arrays.asList(1L, 2L);
            CartPromotionItem item1 = buildCartPromotionItem(1L, 100L, 1001L,
                    new BigDecimal("99.00"), 2, new BigDecimal("10.00"), 50);
            CartPromotionItem item2 = buildCartPromotionItem(2L, 200L, 2001L,
                    new BigDecimal("50.00"), 1, new BigDecimal("5.00"), 30);
            List<CartPromotionItem> cartItems = Arrays.asList(item1, item2);
            List<UmsMemberReceiveAddress> addresses = Collections.singletonList(buildAddress(1L));
            List<SmsCouponHistoryDetail> coupons = Collections.emptyList();
            UmsIntegrationConsumeSetting setting = buildIntegrationSetting();

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(cartItems);
            when(memberReceiveAddressService.list()).thenReturn(addresses);
            when(memberCouponService.listCart(cartItems, 1)).thenReturn(coupons);
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            // When
            ConfirmOrderResult result = orderService.generateConfirmOrder(cartIds);

            // Then
            assertNotNull(result);
            assertEquals(2, result.getCartPromotionItemList().size());
            assertEquals(1, result.getMemberReceiveAddressList().size());
            assertEquals(1000, result.getMemberIntegration());
            assertNotNull(result.getIntegrationConsumeSetting());
            assertNotNull(result.getCalcAmount());
            // 验证金额计算：item1: 99*2=198, item2: 50*1=50; total=248; promotion=10*2+5*1=25; pay=248-25=223
            assertEquals(new BigDecimal("248.0"), result.getCalcAmount().getTotalAmount().setScale(1));
            assertEquals(new BigDecimal("25.0"), result.getCalcAmount().getPromotionAmount().setScale(1));
            assertEquals(new BigDecimal("223.0"), result.getCalcAmount().getPayAmount().setScale(1));
            assertEquals(new BigDecimal("0"), result.getCalcAmount().getFreightAmount());
        }

        @Test
        @DisplayName("正常流程：购物车有商品，用户无积分，确认单仍应正常返�?")
        void shouldReturnConfirmOrderWhenMemberHasNoIntegration() {
            // Given
            mockMember.setIntegration(null);
            List<Long> cartIds = Collections.singletonList(1L);
            CartPromotionItem item = buildCartPromotionItem(1L, 100L, 1001L,
                    new BigDecimal("199.00"), 1, BigDecimal.ZERO, 10);
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));
            when(memberReceiveAddressService.list()).thenReturn(Collections.emptyList());
            when(memberCouponService.listCart(any(), eq(1))).thenReturn(Collections.emptyList());
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(buildIntegrationSetting());

            // When
            ConfirmOrderResult result = orderService.generateConfirmOrder(cartIds);

            // Then
            assertNotNull(result);
            assertNull(result.getMemberIntegration());
        }
    }

    @Nested
    @DisplayName("generateConfirmOrder - 参数边界")
    class GenerateConfirmOrder_Boundary {

        @Test
        @DisplayName("边界：cartIds为空列表，应返回空确认单")
        void shouldReturnEmptyConfirmOrderWhenCartIdsIsEmpty() {
            List<Long> cartIds = Collections.emptyList();
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.emptyList());
            when(memberReceiveAddressService.list()).thenReturn(Collections.emptyList());
            when(memberCouponService.listCart(Collections.emptyList(), 1)).thenReturn(Collections.emptyList());
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(buildIntegrationSetting());

            ConfirmOrderResult result = orderService.generateConfirmOrder(cartIds);

            assertNotNull(result);
            assertTrue(result.getCartPromotionItemList().isEmpty());
            assertNotNull(result.getCalcAmount());
            assertEquals(new BigDecimal("0"), result.getCalcAmount().getTotalAmount());
            assertEquals(new BigDecimal("0"), result.getCalcAmount().getPayAmount());
        }

        @Test
        @DisplayName("边界：null cartIds (会传递到 cartItemService)")
        void shouldHandleNullCartIds() {
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(eq(1L), isNull())).thenReturn(Collections.emptyList());
            when(memberReceiveAddressService.list()).thenReturn(Collections.emptyList());
            when(memberCouponService.listCart(any(), eq(1))).thenReturn(Collections.emptyList());
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(buildIntegrationSetting());

            ConfirmOrderResult result = orderService.generateConfirmOrder(null);

            assertNotNull(result);
            assertTrue(result.getCartPromotionItemList().isEmpty());
        }
    }

    // ============ 第二部分：generateOrder ============

    @Nested
    @DisplayName("generateOrder - 正常流程")
    class GenerateOrder_Normal {

        /**
         * 辅助方法：设�? generateOrder 的公�? mock
         */
        private void setupGenerateOrderMocks(List<Long> cartIds, List<CartPromotionItem> cartItems,
                                              Long addressId) {
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(cartItems);
            when(memberReceiveAddressService.getItem(addressId)).thenReturn(buildAddress(addressId));
            when(orderSettingMapper.selectByExample(any())).thenReturn(Collections.emptyList());
            OmsOrderSetting orderSetting = new OmsOrderSetting();
            orderSetting.setId(1L);
            orderSetting.setNormalOrderOvertime(120);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(orderSetting);
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);
            // mock orderMapper.insert 设置 order.getId()
            doAnswer(invocation -> {
                OmsOrder o = invocation.getArgument(0);
                o.setId(888L);
                return 1;
            }).when(orderMapper).insert(any(OmsOrder.class));
            when(orderItemDao.insertList(anyList())).thenReturn(1);
            doNothing().when(cancelOrderSender).sendMessage(anyLong(), anyLong());
            when(cartItemService.delete(anyLong(), anyList())).thenReturn(0);
        }

        @Test
        @DisplayName("正常流程：不使用优惠券、不使用积分下单")
        void shouldGenerateOrderWithoutCouponAndIntegration() {
            // Given
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("200.00"), 1, 20);
            Long addressId = 1L;

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(addressId);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(null);
            orderParam.setUseIntegration(null);

            setupGenerateOrderMocks(cartIds, Collections.singletonList(item), addressId);
            // stock check
            PmsSkuStock skuStock = new PmsSkuStock();
            skuStock.setLockStock(0);
            when(skuStockMapper.selectByPrimaryKey(1001L)).thenReturn(skuStock);
            when(skuStockMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            // When
            Map<String, Object> result = orderService.generateOrder(orderParam);

            // Then
            assertNotNull(result);
            OmsOrder order = (OmsOrder) result.get("order");
            assertNotNull(order);
            assertEquals(0, order.getStatus()); // 待付�?
            assertEquals(1, order.getPayType());
            assertEquals(new BigDecimal("0"), order.getCouponAmount());
            assertEquals(new BigDecimal("0"), order.getIntegrationAmount());
            assertNull(order.getUseIntegration());
            assertEquals(mockMember.getId(), order.getMemberId());
            assertNotNull(order.getOrderSn());

            // 验证业务状态：订单已插�?
            verify(orderMapper).insert(any(OmsOrder.class));
            // 验证订单项已插入
            verify(orderItemDao).insertList(anyList());
            // 验证购物车已删除
            verify(cartItemService).delete(eq(1L), anyList());
            // 验证发送延迟消�?
            verify(cancelOrderSender).sendMessage(eq(888L), anyLong());
        }

        @Test
        @DisplayName("正常流程：使用优惠券和使用积分下�?")
        void shouldGenerateOrderWithCouponAndIntegration() {
            // Given
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("300.00"), 1, 50);
            Long addressId = 1L;
            Long couponId = 10L;

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(addressId);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(2);
            orderParam.setCouponId(couponId);
            orderParam.setUseIntegration(200);

            setupGenerateOrderMocks(cartIds, Collections.singletonList(item), addressId);

            // mock 优惠�?
            SmsCoupon coupon = new SmsCoupon();
            coupon.setId(couponId);
            coupon.setAmount(new BigDecimal("50.00"));
            coupon.setUseType(0); // 全场通用
            SmsCouponHistoryDetail historyDetail = new SmsCouponHistoryDetail();
            historyDetail.setCoupon(coupon);
            when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(historyDetail));

            // mock 积分设置
            UmsIntegrationConsumeSetting integrationSetting = buildIntegrationSetting();
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(integrationSetting);

            // mock stock
            PmsSkuStock skuStock = new PmsSkuStock();
            skuStock.setLockStock(0);
            when(skuStockMapper.selectByPrimaryKey(1001L)).thenReturn(skuStock);
            when(skuStockMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            // mock 优惠券状态更�?
            when(couponHistoryMapper.selectByExample(any())).thenReturn(Collections.emptyList());

            // When
            Map<String, Object> result = orderService.generateOrder(orderParam);

            // Then
            assertNotNull(result);
            OmsOrder order = (OmsOrder) result.get("order");

            // 验证订单业务状�?
            assertEquals(0, order.getStatus());
            assertEquals(couponId, order.getCouponId());
            assertTrue(order.getCouponAmount().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(order.getIntegrationAmount().compareTo(BigDecimal.ZERO) > 0);
            assertEquals(200, order.getUseIntegration().intValue());

            // 验证积分扣减
            verify(memberService).updateIntegration(eq(1L), eq(800)); // 1000-200
        }
    }

    @Nested
    @DisplayName("generateOrder - 参数边界")
    class GenerateOrder_Boundary {

        @Test
        @DisplayName("边界：收货地址ID为null，应抛出ApiException")
        void shouldThrowExceptionWhenAddressIdIsNull() {
            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(null);

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("请选择收货地址�?", exception.getMessage());
        }

        @Test
        @DisplayName("边界：购物车商品库存不足，应抛出ApiException")
        void shouldThrowExceptionWhenStockInsufficient() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 5, 2); // stock=2 < quantity=5

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("库存不足，无法下�?", exception.getMessage());
        }

        @Test
        @DisplayName("边界：优惠券不可用，应抛出ApiException")
        void shouldThrowExceptionWhenCouponNotAvailable() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(999L); // 不存在的优惠�?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));
            when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.emptyList());

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("该优惠券不可�?", exception.getMessage());
        }

        @Test
        @DisplayName("边界：积分不可用（超过用户积分），应抛出ApiException")
        void shouldThrowExceptionWhenIntegrationExceeds() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(null);
            orderParam.setUseIntegration(2000); // 超过用户积分1000

            mockMember.setIntegration(1000);
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("积分不可�?", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("generateOrder - 异常分支")
    class GenerateOrder_Exception {

        @Test
        @DisplayName("异常：积分不足最低使用门槛，积分返回0不可�?")
        void shouldFailWhenIntegrationBelowThreshold() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(null);
            orderParam.setUseIntegration(50); // 低于 useUnit=100

            mockMember.setIntegration(500);
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));
            UmsIntegrationConsumeSetting setting = buildIntegrationSetting();
            setting.setUseUnit(100);
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("积分不可�?", exception.getMessage());
        }

        @Test
        @DisplayName("异常：积分超过最大抵扣百分比，积分不可用")
        void shouldFailWhenIntegrationExceedsMaxPercent() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(null);
            orderParam.setUseIntegration(6000); // 6000/100=60�? > 100*50%=50�?

            mockMember.setIntegration(10000);
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));
            UmsIntegrationConsumeSetting setting = buildIntegrationSetting();
            setting.setMaxPercentPerOrder(50);
            setting.setUseUnit(100);
            setting.setCouponStatus(1);
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("积分不可�?", exception.getMessage());
        }
    }

    // ============ 第三部分：paySuccess ============

    @Nested
    @DisplayName("paySuccess")
    class PaySuccessTest {

        @Test
        @DisplayName("正常流程：支付成功，更新订单状态为待发货，扣减真实库存")
        void shouldUpdateOrderStatusAndDeductStock() {
            Long orderId = 100L;
            Integer payType = 1;

            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderItemList(Collections.emptyList());
            when(portalOrderDao.getDetail(orderId)).thenReturn(detail);
            when(portalOrderDao.updateSkuStock(anyList())).thenReturn(5);

            Integer count = orderService.paySuccess(orderId, payType);

            assertEquals(5, count);
            // 验证订单状态被设置�?1（待发货�?
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder captured = orderCaptor.getValue();
            assertEquals(orderId, captured.getId());
            assertEquals(1, captured.getStatus());
            assertEquals(payType, captured.getPayType());
            assertNotNull(captured.getPaymentTime());
        }

        @Test
        @DisplayName("边界：订单无商品项，返回0")
        void shouldReturnZeroWhenNoOrderItems() {
            Long orderId = 200L;
            when(orderMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderItemList(Collections.emptyList());
            when(portalOrderDao.getDetail(orderId)).thenReturn(detail);
            when(portalOrderDao.updateSkuStock(anyList())).thenReturn(0);

            Integer count = orderService.paySuccess(orderId, 2);

            assertEquals(0, count);
        }
    }

    // ============ 第四部分：cancelTimeOutOrder ============

    @Nested
    @DisplayName("cancelTimeOutOrder")
    class CancelTimeOutOrderTest {

        @Test
        @DisplayName("正常流程：存在超时订单，取消并返还库�?/优惠�?/积分")
        void shouldCancelTimeoutOrdersAndRefund() {
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setId(1L);
            setting.setNormalOrderOvertime(120);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            OmsOrderDetail detail1 = new OmsOrderDetail();
            detail1.setId(1L);
            detail1.setMemberId(10L);
            detail1.setCouponId(null);
            detail1.setUseIntegration(null); // 未使用积�?
            detail1.setOrderItemList(Collections.emptyList());

            OmsOrderDetail detail2 = new OmsOrderDetail();
            detail2.setId(2L);
            detail2.setMemberId(20L);
            detail2.setCouponId(5L);
            detail2.setUseIntegration(100);
            detail2.setOrderItemList(Collections.emptyList());

            List<OmsOrderDetail> timeOutOrders = Arrays.asList(detail1, detail2);
            when(portalOrderDao.getTimeOutOrders(120)).thenReturn(timeOutOrders);
            when(portalOrderDao.updateOrderStatus(anyList(), eq(4))).thenReturn(2);
            when(portalOrderDao.releaseSkuStockLock(anyList())).thenReturn(1);
            when(couponHistoryMapper.selectByExample(any())).thenReturn(Collections.emptyList());

            // detail2 使用积分的会�?
            UmsMember member2 = new UmsMember();
            member2.setId(20L);
            member2.setIntegration(500);
            when(memberService.getById(20L)).thenReturn(member2);
            doNothing().when(memberService).updateIntegration(eq(20L), eq(600));

            Integer count = orderService.cancelTimeOutOrder();

            assertEquals(2, count);
            // 验证订单状态被批量更新�?4
            verify(portalOrderDao).updateOrderStatus(anyList(), eq(4));
            // 验证库存已释�?
            verify(portalOrderDao, times(2)).releaseSkuStockLock(anyList());
            // 验证积分返还
            verify(memberService).updateIntegration(eq(20L), eq(600));
        }

        @Test
        @DisplayName("边界：无超时订单，返�?0")
        void shouldReturnZeroWhenNoTimeoutOrders() {
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setId(1L);
            setting.setNormalOrderOvertime(60);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);
            when(portalOrderDao.getTimeOutOrders(60)).thenReturn(Collections.emptyList());

            Integer count = orderService.cancelTimeOutOrder();

            assertEquals(0, count);
            verify(portalOrderDao, never()).updateOrderStatus(anyList(), anyInt());
        }
    }

    // ============ 第五部分：cancelOrder ============

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrderTest {

        @Test
        @DisplayName("正常流程：存在待付款订单，取消成�?")
        void shouldCancelExistingOrder() {
            Long orderId = 100L;
            OmsOrder cancelOrder = new OmsOrder();
            cancelOrder.setId(orderId);
            cancelOrder.setStatus(0);
            cancelOrder.setDeleteStatus(0);
            cancelOrder.setMemberId(1L);
            cancelOrder.setCouponId(null);
            cancelOrder.setUseIntegration(null);

            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.singletonList(cancelOrder));
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderItemMapper.selectByExample(any(OmsOrderItemExample.class)))
                    .thenReturn(Collections.singletonList(new OmsOrderItem()));
            when(portalOrderDao.releaseSkuStockLock(anyList())).thenReturn(1);

            orderService.cancelOrder(orderId);

            // 验证订单状态改�?4（已关闭�?
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            assertEquals(4, orderCaptor.getValue().getStatus());
            // 验证库存释放
            verify(portalOrderDao).releaseSkuStockLock(anyList());
        }

        @Test
        @DisplayName("边界：订单不存在或状态不�?0，静默返�?")
        void shouldSilentlyReturnWhenOrderNotFound() {
            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.emptyList());

            orderService.cancelOrder(999L);

            // 不应调用任何后续操作
            verify(orderMapper, never()).updateByPrimaryKeySelective(any());
            verify(portalOrderDao, never()).releaseSkuStockLock(anyList());
        }

        @Test
        @DisplayName("边界：订单存在但deleteStatus不为0，静默返�?")
        void shouldSilentlyReturnWhenDeleteStatusNotZero() {
            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.emptyList());

            orderService.cancelOrder(100L);

            verify(orderMapper, never()).updateByPrimaryKeySelective(any());
        }
    }

    // ============ 第六部分：confirmReceiveOrder ============

    @Nested
    @DisplayName("confirmReceiveOrder")
    class ConfirmReceiveOrderTest {

        @Test
        @DisplayName("正常流程：用户确认自己的已发货订�?")
        void shouldConfirmOwnShippedOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(2); // 已发�?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);
            when(orderMapper.updateByPrimaryKey(any(OmsOrder.class))).thenReturn(1);

            orderService.confirmReceiveOrder(orderId);

            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKey(captor.capture());
            OmsOrder updated = captor.getValue();
            assertEquals(3, updated.getStatus()); // 已完�?
            assertEquals(1, updated.getConfirmStatus());
            assertNotNull(updated.getReceiveTime());
        }

        @Test
        @DisplayName("异常：确认他人订单，应抛出ApiException")
        void shouldThrowExceptionWhenConfirmOthersOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(999L); // 不是当前用户

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.confirmReceiveOrder(orderId));
            assertEquals("不能确认他人订单�?", ex.getMessage());
        }

        @Test
        @DisplayName("异常：订单未发货，应抛出ApiException")
        void shouldThrowExceptionWhenOrderNotShipped() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(1); // 待发货，不是2

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.confirmReceiveOrder(orderId));
            assertEquals("该订单还未发货！", ex.getMessage());
        }
    }

    // ============ 第七部分：deleteOrder ============

    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrderTest {

        @Test
        @DisplayName("正常流程：删除自己已完成订单")
        void shouldDeleteOwnCompletedOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(3); // 已完�?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);
            when(orderMapper.updateByPrimaryKey(any(OmsOrder.class))).thenReturn(1);

            orderService.deleteOrder(orderId);

            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKey(captor.capture());
            assertEquals(1, captor.getValue().getDeleteStatus());
        }

        @Test
        @DisplayName("正常流程：删除自己已关闭订单")
        void shouldDeleteOwnClosedOrder() {
            Long orderId = 200L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(4); // 已关�?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);
            when(orderMapper.updateByPrimaryKey(any(OmsOrder.class))).thenReturn(1);

            orderService.deleteOrder(orderId);

            verify(orderMapper).updateByPrimaryKey(any(OmsOrder.class));
        }

        @Test
        @DisplayName("异常：删除他人订单，应抛出ApiException")
        void shouldThrowExceptionWhenDeleteOthersOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(999L);

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.deleteOrder(orderId));
            assertEquals("不能删除他人订单�?", ex.getMessage());
        }

        @Test
        @DisplayName("异常：删除待付款订单，应抛出ApiException")
        void shouldThrowExceptionWhenOrderIsPending() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(0); // 待付款，�?3/4

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.deleteOrder(orderId));
            assertEquals("只能删除已完成或已关闭的订单�?", ex.getMessage());
        }
    }

    // ============ 第八部分：detail ============

    @Nested
    @DisplayName("detail")
    class DetailTest {

        @Test
        @DisplayName("正常流程：获取订单详情，含订单项")
        void shouldReturnOrderDetailWithItems() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setOrderSn("ORDER001");
            order.setStatus(0);

            OmsOrderItem item1 = new OmsOrderItem();
            item1.setId(1L);
            item1.setOrderId(orderId);
            item1.setProductName("商品1");
            OmsOrderItem item2 = new OmsOrderItem();
            item2.setId(2L);
            item2.setOrderId(orderId);
            item2.setProductName("商品2");
            List<OmsOrderItem> items = Arrays.asList(item1, item2);

            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);
            when(orderItemMapper.selectByExample(any(OmsOrderItemExample.class))).thenReturn(items);

            OmsOrderDetail detail = orderService.detail(orderId);

            assertNotNull(detail);
            assertEquals(orderId, detail.getId());
            assertEquals("ORDER001", detail.getOrderSn());
            assertEquals(2, detail.getOrderItemList().size());
        }

        @Test
        @DisplayName("边界：订单不存在，返回detail为null")
        void shouldHandleNullOrder() {
            Long orderId = 999L;
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(null);
            when(orderItemMapper.selectByExample(any())).thenReturn(Collections.emptyList());

            OmsOrderDetail detail = orderService.detail(orderId);

            assertNotNull(detail); // BeanUtil.copyProperties 创建了空对象
            assertNull(detail.getId());
        }
    }

    // ============ 第九部分：list ============

    @Nested
    @DisplayName("list")
    class ListTest {

        @Test
        @DisplayName("正常流程：status=-1 查询所有订单，返回分页结果")
        void shouldListAllOrdersWhenStatusIsMinusOne() {
            try (MockedStatic<PageHelper> pageHelperMock = mockStatic(PageHelper.class)) {
                OmsOrder order1 = new OmsOrder();
                order1.setId(1L);
                order1.setOrderSn("ORD001");
                order1.setStatus(0);

                OmsOrder order2 = new OmsOrder();
                order2.setId(2L);
                order2.setOrderSn("ORD002");
                order2.setStatus(1);

                List<OmsOrder> orders = Arrays.asList(order1, order2);

                when(memberService.getCurrentMember()).thenReturn(mockMember);
                when(orderMapper.selectByExample(any(OmsOrderExample.class))).thenReturn(orders);

                OmsOrderItem item1 = new OmsOrderItem();
                item1.setId(1L);
                item1.setOrderId(1L);
                OmsOrderItem item2 = new OmsOrderItem();
                item2.setId(2L);
                item2.setOrderId(2L);
                when(orderItemMapper.selectByExample(any(OmsOrderItemExample.class)))
                        .thenReturn(Arrays.asList(item1, item2));

                CommonPage<OmsOrderDetail> result = orderService.list(-1, 1, 10);

                assertNotNull(result);
                assertEquals(2, result.getList().size());
                // 验证 status=-1 被转�? null
                verify(memberService).getCurrentMember();
            }
        }

        @Test
        @DisplayName("边界：查询结果为空，返回空分�?")
        void shouldReturnEmptyPageWhenNoOrders() {
            try (MockedStatic<PageHelper> pageHelperMock = mockStatic(PageHelper.class)) {
                when(memberService.getCurrentMember()).thenReturn(mockMember);
                when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                        .thenReturn(Collections.emptyList());

                CommonPage<OmsOrderDetail> result = orderService.list(0, 1, 10);

                assertNotNull(result);
                assertNull(result.getList()); // CollUtil.isEmpty �? return resultPage without setList
            }
        }
    }

    // ============ 第十部分：paySuccessByOrderSn ============

    @Nested
    @DisplayName("paySuccessByOrderSn")
    class PaySuccessByOrderSnTest {

        @Test
        @DisplayName("正常流程：根据orderSn找到待付款订单，支付成功")
        void shouldPaySuccessByOrderSn() {
            String orderSn = "202607290001";
            OmsOrder order = new OmsOrder();
            order.setId(100L);
            order.setOrderSn(orderSn);
            order.setStatus(0);

            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.singletonList(order));
            when(orderMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderItemList(Collections.emptyList());
            when(portalOrderDao.getDetail(100L)).thenReturn(detail);
            when(portalOrderDao.updateSkuStock(anyList())).thenReturn(1);

            orderService.paySuccessByOrderSn(orderSn, 1);

            verify(orderMapper).updateByPrimaryKeySelective(any(OmsOrder.class));
            verify(portalOrderDao).getDetail(100L);
        }

        @Test
        @DisplayName("边界：orderSn不存在或订单状态不�?0，静默返回不执行")
        void shouldNotPayWhenOrderNotFound() {
            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.emptyList());

            orderService.paySuccessByOrderSn("NOTEXIST", 1);

            verify(orderMapper, never()).updateByPrimaryKeySelective(any());
        }
    }

    // ============ 第十一部分：sendDelayMessageCancelOrder ============

    @Nested
    @DisplayName("sendDelayMessageCancelOrder")
    class SendDelayMessageTest {

        @Test
        @DisplayName("正常流程：发送带超时时间的延迟消�?")
        void shouldSendDelayMessageWithCorrectTimeout() {
            Long orderId = 100L;
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setId(1L);
            setting.setNormalOrderOvertime(120); // 120分钟
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            orderService.sendDelayMessageCancelOrder(orderId);

            long expectedDelay = 120L * 60 * 1000;
            verify(cancelOrderSender).sendMessage(orderId, expectedDelay);
        }

        @Test
        @DisplayName("边界：超时时间为0，发�?0延迟消息")
        void shouldSendZeroDelayWhenTimeoutIsZero() {
            Long orderId = 200L;
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setId(1L);
            setting.setNormalOrderOvertime(0);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            orderService.sendDelayMessageCancelOrder(orderId);

            verify(cancelOrderSender).sendMessage(orderId, 0L);
        }
    }

    // ============ 第十二部分：并发场景 ============

    @Nested
    @DisplayName("并发场景")
    class ConcurrentTests {

        @Test
        @DisplayName("并发：多个线程同时调�? cancelOrder，无异常且状态一�?")
        void concurrentCancelOrderShouldBeThreadSafe() throws Exception {
            Long orderId = 100L;
            OmsOrder cancelOrder = new OmsOrder();
            cancelOrder.setId(orderId);
            cancelOrder.setStatus(0);
            cancelOrder.setDeleteStatus(0);
            cancelOrder.setMemberId(1L);
            cancelOrder.setCouponId(null);
            cancelOrder.setUseIntegration(null);

            // 第一个线程成功，后续线程 selectByExample 返回�?
            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.singletonList(cancelOrder))
                    .thenReturn(Collections.emptyList());
            when(orderMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
            when(orderItemMapper.selectByExample(any()))
                    .thenReturn(Collections.emptyList());

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        orderService.cancelOrder(orderId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(threadCount, successCount.get());
            assertEquals(0, errorCount.get());
            // 业务状态验证：至少调用了一�? updateByPrimaryKeySelective（订单状态改�?4�?
            verify(orderMapper, atLeastOnce()).updateByPrimaryKeySelective(any());
        }

        @Test
        @DisplayName("并发：多个线程同时调�? generateConfirmOrder，返回一致的确认�?")
        void concurrentGenerateConfirmOrderShouldBeThreadSafe() throws Exception {
            List<Long> cartIds = Collections.singletonList(1L);
            CartPromotionItem item = buildCartPromotionItem(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, new BigDecimal("10.00"), 30);

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));
            when(memberReceiveAddressService.list()).thenReturn(Collections.emptyList());
            when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.emptyList());
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(buildIntegrationSetting());

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<ConfirmOrderResult> results = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        ConfirmOrderResult r = orderService.generateConfirmOrder(cartIds);
                        results.add(r);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(threadCount, results.size());
            // 所有线程返回的金额应一�?
            for (ConfirmOrderResult r : results) {
                assertNotNull(r.getCalcAmount());
                assertEquals(new BigDecimal("100").setScale(0), r.getCalcAmount().getTotalAmount().setScale(0));
                assertEquals(new BigDecimal("10").setScale(0), r.getCalcAmount().getPromotionAmount().setScale(0));
            }
        }
    }
}
