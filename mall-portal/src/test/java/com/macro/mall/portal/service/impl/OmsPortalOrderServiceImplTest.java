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
 * OmsPortalOrderServiceImpl 閸楁洖鍘撳ù瀣槸
 * 鐟曞棛娲婇敍姘劀鐢憡绁︾粙瀣ㄢ偓浣稿棘閺佹媽绔熼悾灞烩偓浣哥磽鐢鍨庨弨顖樷偓浣歌嫙閸欐垵婧€閺?
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OmsPortalOrderServiceImpl 閸楁洖鍘撳ù瀣槸")
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

        // 濞夈劌鍙?@Value 鐏炵偞鈧?
        ReflectionTestUtils.setField(orderService, "REDIS_KEY_ORDER_ID", "orderId");
        ReflectionTestUtils.setField(orderService, "REDIS_DATABASE", "mall");
    }

    // ============ 鏉堝懎濮弬瑙勭《閿涙碍鐎鐑樼ゴ鐠囨洘鏆熼幑? ============

    private CartPromotionItem buildCartPromotionItem(Long id, Long productId, Long skuId,
                                                      BigDecimal price, Integer quantity,
                                                      BigDecimal reduceAmount, Integer realStock) {
        CartPromotionItem item = new CartPromotionItem();
        item.setId(id);
        item.setProductId(productId);
        item.setProductName("閸熷棗鎼? + productId);
        item.setProductPic("pic.jpg");
        item.setProductAttr("妫版粏澹?缁?");
        item.setProductBrand("閸濅胶澧滱");
        item.setProductSn("SN" + productId);
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setProductSkuId(skuId);
        item.setProductSkuCode("SKU" + skuId);
        item.setProductCategoryId(1L);
        item.setReduceAmount(reduceAmount);
        item.setPromotionMessage("濠娾€冲櫤娣囧啴鏀?);
        item.setIntegration(10);
        item.setGrowth(5);
        item.setRealStock(realStock);
        return item;
    }

    private UmsMemberReceiveAddress buildAddress(Long id) {
        UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
        address.setId(id);
        address.setName("瀵姳绗?);
        address.setPhoneNumber("13800138000");
        address.setPostCode("100000");
        address.setProvince("楠炲じ绗㈤惇?");
        address.setCity("濞ｅ崬婀风敮?");
        address.setRegion("閸楁鍖楅崠?");
        address.setDetailAddress("缁夋垶濡ч崶顓＄熅1閸?");
        return address;
    }

    private UmsIntegrationConsumeSetting buildIntegrationSetting() {
        UmsIntegrationConsumeSetting setting = new UmsIntegrationConsumeSetting();
        setting.setId(1L);
        setting.setCouponStatus(1); // 1=閸欘垯绗屾导妯诲劕閸掔鍙￠悽?
        setting.setUseUnit(100);
        setting.setMaxPercentPerOrder(50);
        return setting;
    }

    private CartPromotionItem buildCartPromotionItemWithRealStock(Long id, Long productId, Long skuId,
                                                                    BigDecimal price, Integer quantity,
                                                                    Integer realStock) {
        return buildCartPromotionItem(id, productId, skuId, price, quantity, BigDecimal.ZERO, realStock);
    }

    // ============ 缁楊兛绔撮柈銊ュ瀻閿涙enerateConfirmOrder ============

    @Nested
    @DisplayName("generateConfirmOrder - 濮濓絽鐖跺ù浣衡柤")
    class GenerateConfirmOrder_Normal {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙俺鍠橀悧鈺勬簠閺堝鏅㈤崫渚婄礉閻劍鍩涢張澶屝濋崚鍡礉鎼存棁绻戦崶鐐茬暚閺佸鈥樼拋銈呭礋")
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
            // 妤犲矁鐦夐柌鎴︻杺鐠侊紕鐣婚敍姝﹖em1: 99*2=198, item2: 50*1=50; total=248; promotion=10*2+5*1=25; pay=248-25=223
            assertEquals(new BigDecimal("248.0"), result.getCalcAmount().getTotalAmount().setScale(1));
            assertEquals(new BigDecimal("25.0"), result.getCalcAmount().getPromotionAmount().setScale(1));
            assertEquals(new BigDecimal("223.0"), result.getCalcAmount().getPayAmount().setScale(1));
            assertEquals(new BigDecimal("0"), result.getCalcAmount().getFreightAmount());
        }

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙俺鍠橀悧鈺勬簠閺堝鏅㈤崫渚婄礉閻劍鍩涢弮鐘敌濋崚鍡礉绾喛顓婚崡鏇氱矝鎼存梹顒滅敮姝岀箲閸?")
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
    @DisplayName("generateConfirmOrder - 閸欏倹鏆熸潏鍦櫕")
    class GenerateConfirmOrder_Boundary {

        @Test
        @DisplayName("鏉堝湱鏅敍姝漚rtIds娑撹櫣鈹栭崚妤勩€冮敍灞界安鏉╂柨娲栫粚铏光€樼拋銈呭礋")
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
        @DisplayName("鏉堝湱鏅敍姝痷ll cartIds (娴兼矮绱堕柅鎺戝煂 cartItemService)")
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

    // ============ 缁楊兛绨╅柈銊ュ瀻閿涙enerateOrder ============

    @Nested
    @DisplayName("generateOrder - 濮濓絽鐖跺ù浣衡柤")
    class GenerateOrder_Normal {

        /**
         * 鏉堝懎濮弬瑙勭《閿涙俺顔曠純? generateOrder 閻ㄥ嫬鍙曢崗? mock
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
            // mock orderMapper.insert 鐠佸墽鐤?order.getId()
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
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙矮绗夋担璺ㄦ暏娴兼ɑ鍎崚鎼炩偓浣风瑝娴ｈ法鏁ょ粔顖氬瀻娑撳宕?)
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
            assertEquals(0, order.getStatus()); // 瀵板懍绮▎?
            assertEquals(1, order.getPayType());
            assertEquals(new BigDecimal("0"), order.getCouponAmount());
            assertEquals(new BigDecimal("0"), order.getIntegrationAmount());
            assertNull(order.getUseIntegration());
            assertEquals(mockMember.getId(), order.getMemberId());
            assertNotNull(order.getOrderSn());

            // 妤犲矁鐦夋稉姘閻樿埖鈧緤绱扮拋銏犲礋瀹稿弶褰冮崗?
            verify(orderMapper).insert(any(OmsOrder.class));
            // 妤犲矁鐦夌拋銏犲礋妞ょ懓鍑￠幓鎺戝弳
            verify(orderItemDao).insertList(anyList());
            // 妤犲矁鐦夌拹顓犲⒖鏉烇箑鍑￠崚鐘绘珟
            verify(cartItemService).delete(eq(1L), anyList());
            // 妤犲矁鐦夐崣鎴︹偓浣告鏉╃喐绉烽幁?
            verify(cancelOrderSender).sendMessage(eq(888L), anyLong());
        }

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙矮濞囬悽銊ょ喘閹姴鍩滈崪灞煎▏閻劎袧閸掑棔绗呴崡?")
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

            // mock 娴兼ɑ鍎崚?
            SmsCoupon coupon = new SmsCoupon();
            coupon.setId(couponId);
            coupon.setAmount(new BigDecimal("50.00"));
            coupon.setUseType(0); // 閸忋劌婧€闁氨鏁?
            SmsCouponHistoryDetail historyDetail = new SmsCouponHistoryDetail();
            historyDetail.setCoupon(coupon);
            when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(historyDetail));

            // mock 缁夘垰鍨庣拋鍓х枂
            UmsIntegrationConsumeSetting integrationSetting = buildIntegrationSetting();
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(integrationSetting);

            // mock stock
            PmsSkuStock skuStock = new PmsSkuStock();
            skuStock.setLockStock(0);
            when(skuStockMapper.selectByPrimaryKey(1001L)).thenReturn(skuStock);
            when(skuStockMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            // mock 娴兼ɑ鍎崚鍝ュЦ閹焦娲块弬?
            when(couponHistoryMapper.selectByExample(any())).thenReturn(Collections.emptyList());

            // When
            Map<String, Object> result = orderService.generateOrder(orderParam);

            // Then
            assertNotNull(result);
            OmsOrder order = (OmsOrder) result.get("order");

            // 妤犲矁鐦夌拋銏犲礋娑撴艾濮熼悩鑸碘偓?
            assertEquals(0, order.getStatus());
            assertEquals(couponId, order.getCouponId());
            assertTrue(order.getCouponAmount().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(order.getIntegrationAmount().compareTo(BigDecimal.ZERO) > 0);
            assertEquals(200, order.getUseIntegration().intValue());

            // 妤犲矁鐦夌粔顖氬瀻閹碉絽鍣?
            verify(memberService).updateIntegration(eq(1L), eq(800)); // 1000-200
        }
    }

    @Nested
    @DisplayName("generateOrder - 閸欏倹鏆熸潏鍦櫕")
    class GenerateOrder_Boundary {

        @Test
        @DisplayName("鏉堝湱鏅敍姘暪鐠愌冩勾閸р偓ID娑撶皠ull閿涘苯绨查幎娑樺毉ApiException")
        void shouldThrowExceptionWhenAddressIdIsNull() {
            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(null);

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("鐠囩兘鈧瀚ㄩ弨鎯版彛閸︽澘娼冮敍?", exception.getMessage());
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘冲枠閻椻晞婧呴崯鍡楁惂鎼存挸鐡ㄦ稉宥堝喕閿涘苯绨查幎娑樺毉ApiException")
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
            assertEquals("鎼存挸鐡ㄦ稉宥堝喕閿涘本妫ゅ▔鏇氱瑓閸?", exception.getMessage());
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘喘閹姴鍩滄稉宥呭讲閻㈩煉绱濇惔鏃€濮忛崙绡坧iException")
        void shouldThrowExceptionWhenCouponNotAvailable() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(999L); // 娑撳秴鐡ㄩ崷銊ф畱娴兼ɑ鍎崚?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));
            when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.emptyList());

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("鐠囥儰绱幆鐘插煖娑撳秴褰查悽?", exception.getMessage());
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘毙濋崚鍡曠瑝閸欘垳鏁ら敍鍫ｇТ鏉╁洨鏁ら幋椋幮濋崚鍡礆閿涘苯绨查幎娑樺毉ApiException")
        void shouldThrowExceptionWhenIntegrationExceeds() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(null);
            orderParam.setUseIntegration(2000); // 鐡掑懓绻冮悽銊﹀煕缁夘垰鍨?000

            mockMember.setIntegration(1000);
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("缁夘垰鍨庢稉宥呭讲閻?", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("generateOrder - 瀵倸鐖堕崚鍡樻暜")
    class GenerateOrder_Exception {

        @Test
        @DisplayName("瀵倸鐖堕敍姘毙濋崚鍡曠瑝鐡掕櫕娓舵担搴濆▏閻劑妫Σ娑崇礉缁夘垰鍨庢潻鏂挎礀0娑撳秴褰查悽?")
        void shouldFailWhenIntegrationBelowThreshold() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(null);
            orderParam.setUseIntegration(50); // 娴ｅ簼绨?useUnit=100

            mockMember.setIntegration(500);
            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(cartItemService.listPromotion(1L, cartIds)).thenReturn(Collections.singletonList(item));
            UmsIntegrationConsumeSetting setting = buildIntegrationSetting();
            setting.setUseUnit(100);
            when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            ApiException exception = assertThrows(ApiException.class,
                    () -> orderService.generateOrder(orderParam));
            assertEquals("缁夘垰鍨庢稉宥呭讲閻?", exception.getMessage());
        }

        @Test
        @DisplayName("瀵倸鐖堕敍姘毙濋崚鍡氱Т鏉╁洦娓舵径褎濮烽幍锝囨閸掑棙鐦敍宀€袧閸掑棔绗夐崣顖滄暏")
        void shouldFailWhenIntegrationExceedsMaxPercent() {
            List<Long> cartIds = Arrays.asList(1L);
            CartPromotionItem item = buildCartPromotionItemWithRealStock(1L, 100L, 1001L,
                    new BigDecimal("100.00"), 1, 50);

            OrderParam orderParam = new OrderParam();
            orderParam.setMemberReceiveAddressId(1L);
            orderParam.setCartIds(cartIds);
            orderParam.setPayType(1);
            orderParam.setCouponId(null);
            orderParam.setUseIntegration(6000); // 6000/100=60閸? > 100*50%=50閸?

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
            assertEquals("缁夘垰鍨庢稉宥呭讲閻?", exception.getMessage());
        }
    }

    // ============ 缁楊兛绗侀柈銊ュ瀻閿涙aySuccess ============

    @Nested
    @DisplayName("paySuccess")
    class PaySuccessTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙碍鏁禒妯诲灇閸旂噦绱濋弴瀛樻煀鐠併垹宕熼悩鑸碘偓浣疯礋瀵板懎褰傜拹褝绱濋幍锝呭櫤閻喎鐤勬惔鎾崇摠")
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
            // 妤犲矁鐦夌拋銏犲礋閻樿埖鈧浇顫︾拋鍓х枂娑?1閿涘牆绶熼崣鎴ｆ彛閿?
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder captured = orderCaptor.getValue();
            assertEquals(orderId, captured.getId());
            assertEquals(1, captured.getStatus());
            assertEquals(payType, captured.getPayType());
            assertNotNull(captured.getPaymentTime());
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘愁吂閸楁洘妫ら崯鍡楁惂妞ょ櫢绱濇潻鏂挎礀0")
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

    // ============ 缁楊剙娲撻柈銊ュ瀻閿涙瓭ancelTimeOutOrder ============

    @Nested
    @DisplayName("cancelTimeOutOrder")
    class CancelTimeOutOrderTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙艾鐡ㄩ崷銊ㄧТ閺冩儼顓归崡鏇礉閸欐牗绉烽獮鎯扮箲鏉╂ê绨辩€?/娴兼ɑ鍎崚?/缁夘垰鍨?)
        void shouldCancelTimeoutOrdersAndRefund() {
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setId(1L);
            setting.setNormalOrderOvertime(120);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            OmsOrderDetail detail1 = new OmsOrderDetail();
            detail1.setId(1L);
            detail1.setMemberId(10L);
            detail1.setCouponId(null);
            detail1.setUseIntegration(null); // 閺堫亙濞囬悽銊濋崚?
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

            // detail2 娴ｈ法鏁ょ粔顖氬瀻閻ㄥ嫪绱伴崨?
            UmsMember member2 = new UmsMember();
            member2.setId(20L);
            member2.setIntegration(500);
            when(memberService.getById(20L)).thenReturn(member2);
            doNothing().when(memberService).updateIntegration(eq(20L), eq(600));

            Integer count = orderService.cancelTimeOutOrder();

            assertEquals(2, count);
            // 妤犲矁鐦夌拋銏犲礋閻樿埖鈧浇顫﹂幍褰掑櫤閺囧瓨鏌婃稉?4
            verify(portalOrderDao).updateOrderStatus(anyList(), eq(4));
            // 妤犲矁鐦夋惔鎾崇摠瀹告煡鍣撮弨?
            verify(portalOrderDao, times(2)).releaseSkuStockLock(anyList());
            // 妤犲矁鐦夌粔顖氬瀻鏉╂棁绻?
            verify(memberService).updateIntegration(eq(20L), eq(600));
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘￥鐡掑懏妞傜拋銏犲礋閿涘矁绻戦崶?0")
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

    // ============ 缁楊兛绨查柈銊ュ瀻閿涙瓭ancelOrder ============

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrderTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙艾鐡ㄩ崷銊ョ窡娴犳ɑ顑欑拋銏犲礋閿涘苯褰囧☉鍫熷灇閸?")
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

            // 妤犲矁鐦夌拋銏犲礋閻樿埖鈧焦鏁兼稉?4閿涘牆鍑￠崗鎶芥４閿?
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            assertEquals(4, orderCaptor.getValue().getStatus());
            // 妤犲矁鐦夋惔鎾崇摠闁插﹥鏂?
            verify(portalOrderDao).releaseSkuStockLock(anyList());
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘愁吂閸楁洑绗夌€涙ê婀幋鏍Ц閹椒绗夐弰?0閿涘矂娼ゆ妯跨箲閸?")
        void shouldSilentlyReturnWhenOrderNotFound() {
            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.emptyList());

            orderService.cancelOrder(999L);

            // 娑撳秴绨茬拫鍐暏娴犺缍嶉崥搴ｇ敾閹垮秳缍?
            verify(orderMapper, never()).updateByPrimaryKeySelective(any());
            verify(portalOrderDao, never()).releaseSkuStockLock(anyList());
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘愁吂閸楁洖鐡ㄩ崷銊ょ稻deleteStatus娑撳秳璐?閿涘矂娼ゆ妯跨箲閸?")
        void shouldSilentlyReturnWhenDeleteStatusNotZero() {
            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.emptyList());

            orderService.cancelOrder(100L);

            verify(orderMapper, never()).updateByPrimaryKeySelective(any());
        }
    }

    // ============ 缁楊剙鍙氶柈銊ュ瀻閿涙瓭onfirmReceiveOrder ============

    @Nested
    @DisplayName("confirmReceiveOrder")
    class ConfirmReceiveOrderTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙氨鏁ら幋椋庘€樼拋銈堝殰瀹歌京娈戝鎻掑絺鐠愌嗩吂閸?")
        void shouldConfirmOwnShippedOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(2); // 瀹告彃褰傜拹?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);
            when(orderMapper.updateByPrimaryKey(any(OmsOrder.class))).thenReturn(1);

            orderService.confirmReceiveOrder(orderId);

            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKey(captor.capture());
            OmsOrder updated = captor.getValue();
            assertEquals(3, updated.getStatus()); // 瀹告彃鐣幋?
            assertEquals(1, updated.getConfirmStatus());
            assertNotNull(updated.getReceiveTime());
        }

        @Test
        @DisplayName("瀵倸鐖堕敍姘扁€樼拋銈勭铂娴滈缚顓归崡鏇礉鎼存梹濮忛崙绡坧iException")
        void shouldThrowExceptionWhenConfirmOthersOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(999L); // 娑撳秵妲歌ぐ鎾冲閻劍鍩?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.confirmReceiveOrder(orderId));
            assertEquals("娑撳秷鍏樼涵顔款吇娴犳牔姹夌拋銏犲礋閿?", ex.getMessage());
        }

        @Test
        @DisplayName("瀵倸鐖堕敍姘愁吂閸楁洘婀崣鎴ｆ彛閿涘苯绨查幎娑樺毉ApiException")
        void shouldThrowExceptionWhenOrderNotShipped() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(1); // 瀵板懎褰傜拹褝绱濇稉宥嗘Ц2

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.confirmReceiveOrder(orderId));
            assertEquals("鐠囥儴顓归崡鏇＄箷閺堫亜褰傜拹褝绱?, ex.getMessage());
        }
    }

    // ============ 缁楊兛绔烽柈銊ュ瀻閿涙瓰eleteOrder ============

    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrderTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙艾鍨归梽銈堝殰瀹稿崬鍑＄€瑰本鍨氱拋銏犲礋")
        void shouldDeleteOwnCompletedOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(3); // 瀹告彃鐣幋?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);
            when(orderMapper.updateByPrimaryKey(any(OmsOrder.class))).thenReturn(1);

            orderService.deleteOrder(orderId);

            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKey(captor.capture());
            assertEquals(1, captor.getValue().getDeleteStatus());
        }

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙艾鍨归梽銈堝殰瀹稿崬鍑￠崗鎶芥４鐠併垹宕?)
        void shouldDeleteOwnClosedOrder() {
            Long orderId = 200L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(4); // 瀹告彃鍙ч梻?

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);
            when(orderMapper.updateByPrimaryKey(any(OmsOrder.class))).thenReturn(1);

            orderService.deleteOrder(orderId);

            verify(orderMapper).updateByPrimaryKey(any(OmsOrder.class));
        }

        @Test
        @DisplayName("瀵倸鐖堕敍姘灩闂勩倓绮禍楦款吂閸楁洩绱濇惔鏃€濮忛崙绡坧iException")
        void shouldThrowExceptionWhenDeleteOthersOrder() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(999L);

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.deleteOrder(orderId));
            assertEquals("娑撳秷鍏橀崚鐘绘珟娴犳牔姹夌拋銏犲礋閿?", ex.getMessage());
        }

        @Test
        @DisplayName("瀵倸鐖堕敍姘灩闂勩倕绶熸禒妯活儥鐠併垹宕熼敍灞界安閹舵稑鍤瑼piException")
        void shouldThrowExceptionWhenOrderIsPending() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setMemberId(1L);
            order.setStatus(0); // 瀵板懍绮▎鎾呯礉闂?3/4

            when(memberService.getCurrentMember()).thenReturn(mockMember);
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(order);

            ApiException ex = assertThrows(ApiException.class,
                    () -> orderService.deleteOrder(orderId));
            assertEquals("閸欘亣鍏橀崚鐘绘珟瀹告彃鐣幋鎰灗瀹告彃鍙ч梻顓犳畱鐠併垹宕熼敍?", ex.getMessage());
        }
    }

    // ============ 缁楊剙鍙撻柈銊ュ瀻閿涙瓰etail ============

    @Nested
    @DisplayName("detail")
    class DetailTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙俺骞忛崣鏍吂閸楁洝顕涢幆鍜冪礉閸氼偉顓归崡鏇€?)
        void shouldReturnOrderDetailWithItems() {
            Long orderId = 100L;
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            order.setOrderSn("ORDER001");
            order.setStatus(0);

            OmsOrderItem item1 = new OmsOrderItem();
            item1.setId(1L);
            item1.setOrderId(orderId);
            item1.setProductName("閸熷棗鎼?");
            OmsOrderItem item2 = new OmsOrderItem();
            item2.setId(2L);
            item2.setOrderId(orderId);
            item2.setProductName("閸熷棗鎼?");
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
        @DisplayName("鏉堝湱鏅敍姘愁吂閸楁洑绗夌€涙ê婀敍宀冪箲閸ョ€宔tail娑撶皠ull")
        void shouldHandleNullOrder() {
            Long orderId = 999L;
            when(orderMapper.selectByPrimaryKey(orderId)).thenReturn(null);
            when(orderItemMapper.selectByExample(any())).thenReturn(Collections.emptyList());

            OmsOrderDetail detail = orderService.detail(orderId);

            assertNotNull(detail); // BeanUtil.copyProperties 閸掓稑缂撴禍鍡欌敄鐎电钖?
            assertNull(detail.getId());
        }
    }

    // ============ 缁楊兛绡€闁劌鍨庨敍姝璱st ============

    @Nested
    @DisplayName("list")
    class ListTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙tatus=-1 閺屻儴顕楅幍鈧張澶庮吂閸楁洩绱濇潻鏂挎礀閸掑棝銆夌紒鎾寸亯")
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
                // 妤犲矁鐦?status=-1 鐞氼偉娴嗘稉? null
                verify(memberService).getCurrentMember();
            }
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘叀鐠囥垻绮ㄩ弸婊€璐熺粚鐚寸礉鏉╂柨娲栫粚鍝勫瀻妞?")
        void shouldReturnEmptyPageWhenNoOrders() {
            try (MockedStatic<PageHelper> pageHelperMock = mockStatic(PageHelper.class)) {
                when(memberService.getCurrentMember()).thenReturn(mockMember);
                when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                        .thenReturn(Collections.emptyList());

                CommonPage<OmsOrderDetail> result = orderService.list(0, 1, 10);

                assertNotNull(result);
                assertNull(result.getList()); // CollUtil.isEmpty 閳? return resultPage without setList
            }
        }
    }

    // ============ 缁楊剙宕勯柈銊ュ瀻閿涙aySuccessByOrderSn ============

    @Nested
    @DisplayName("paySuccessByOrderSn")
    class PaySuccessByOrderSnTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙碍鐗撮幑鐣俽derSn閹垫儳鍩屽鍛帛濞嗘崘顓归崡鏇礉閺€顖欑帛閹存劕濮?)
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
        @DisplayName("鏉堝湱鏅敍姝皉derSn娑撳秴鐡ㄩ崷銊﹀灗鐠併垹宕熼悩鑸碘偓浣风瑝閺?0閿涘矂娼ゆ妯跨箲閸ョ偘绗夐幍褑顢?)
        void shouldNotPayWhenOrderNotFound() {
            when(orderMapper.selectByExample(any(OmsOrderExample.class)))
                    .thenReturn(Collections.emptyList());

            orderService.paySuccessByOrderSn("NOTEXIST", 1);

            verify(orderMapper, never()).updateByPrimaryKeySelective(any());
        }
    }

    // ============ 缁楊剙宕勬稉鈧柈銊ュ瀻閿涙endDelayMessageCancelOrder ============

    @Nested
    @DisplayName("sendDelayMessageCancelOrder")
    class SendDelayMessageTest {

        @Test
        @DisplayName("濮濓絽鐖跺ù浣衡柤閿涙艾褰傞柅浣哥敨鐡掑懏妞傞弮鍫曟？閻ㄥ嫬娆㈡潻鐔哥Х閹?")
        void shouldSendDelayMessageWithCorrectTimeout() {
            Long orderId = 100L;
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setId(1L);
            setting.setNormalOrderOvertime(120); // 120閸掑棝鎸?
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);

            orderService.sendDelayMessageCancelOrder(orderId);

            long expectedDelay = 120L * 60 * 1000;
            verify(cancelOrderSender).sendMessage(orderId, expectedDelay);
        }

        @Test
        @DisplayName("鏉堝湱鏅敍姘崇Т閺冭埖妞傞梻缈犺礋0閿涘苯褰傞柅?0瀵ゆ儼绻滃☉鍫熶紖")
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

    // ============ 缁楊剙宕勬禍宀勫劥閸掑棴绱伴獮璺哄絺閸︾儤娅?============

    @Nested
    @DisplayName("楠炶泛褰傞崷鐑樻珯")
    class ConcurrentTests {

        @Test
        @DisplayName("楠炶泛褰傞敍姘樋娑擃亞鍤庣粙瀣倱閺冩儼鐨熼悽? cancelOrder閿涘本妫ゅ鍌氱埗娑撴梻濮搁幀浣风閼?")
        void concurrentCancelOrderShouldBeThreadSafe() throws Exception {
            Long orderId = 100L;
            OmsOrder cancelOrder = new OmsOrder();
            cancelOrder.setId(orderId);
            cancelOrder.setStatus(0);
            cancelOrder.setDeleteStatus(0);
            cancelOrder.setMemberId(1L);
            cancelOrder.setCouponId(null);
            cancelOrder.setUseIntegration(null);

            // 缁楊兛绔存稉顏嗗殠缁嬪鍨氶崝鐕傜礉閸氬海鐢荤痪璺ㄢ柤 selectByExample 鏉╂柨娲栫粚?
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
            // 娑撴艾濮熼悩鑸碘偓渚€鐛欑拠渚婄窗閼峰啿鐨拫鍐暏娴滃棔绔村▎? updateByPrimaryKeySelective閿涘牐顓归崡鏇犲Ц閹焦鏁兼稉?4閿?
            verify(orderMapper, atLeastOnce()).updateByPrimaryKeySelective(any());
        }

        @Test
        @DisplayName("楠炶泛褰傞敍姘樋娑擃亞鍤庣粙瀣倱閺冩儼鐨熼悽? generateConfirmOrder閿涘矁绻戦崶鐐扮閼峰娈戠涵顔款吇閸?")
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
            // 閹碘偓閺堝鍤庣粙瀣箲閸ョ偟娈戦柌鎴︻杺鎼存柧绔撮懛?
            for (ConfirmOrderResult r : results) {
                assertNotNull(r.getCalcAmount());
                assertEquals(new BigDecimal("100").setScale(0), r.getCalcAmount().getTotalAmount().setScale(0));
                assertEquals(new BigDecimal("10").setScale(0), r.getCalcAmount().getPromotionAmount().setScale(0));
            }
        }
    }
}
