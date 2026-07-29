package com.macro.mall.service.impl;

import com.macro.mall.dao.OmsOrderDao;
import com.macro.mall.dao.OmsOrderOperateHistoryDao;
import com.macro.mall.dto.*;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderOperateHistoryMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderOperateHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OmsOrderServiceImpl unit tests
 * Coverage: normal flow, boundary, exception, concurrency
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OmsOrderServiceImplTest {

    @Mock
    private OmsOrderMapper orderMapper;

    @Mock
    private OmsOrderDao orderDao;

    @Mock
    private OmsOrderOperateHistoryDao orderOperateHistoryDao;

    @Mock
    private OmsOrderOperateHistoryMapper orderOperateHistoryMapper;

    @InjectMocks
    private OmsOrderServiceImpl orderService;

    // Unicode-safe expected strings to avoid encoding issues
    // "完成发货"
    static final String NOTE_DELIVERY = "\u5B8C\u6210\u53D1\u8D27";
    // "修改收货人信息"
    static final String NOTE_RECEIVER = "\u4FEE\u6539\u6536\u8D27\u4EBA\u4FE1\u606F";
    // "修改费用信息"
    static final String NOTE_MONEY = "\u4FEE\u6539\u8D39\u7528\u4FE1\u606F";
    // "后台管理员"
    static final String ADMIN_USER = "\u540E\u53F0\u7BA1\u7406\u5458";
    // "修改备注信息："
    static final String NOTE_PREFIX = "\u4FEE\u6539\u5907\u6CE8\u4FE1\u606F\uFF1A";
    // "订单关闭:"
    static final String CLOSE_PREFIX = "\u8BA2\u5355\u5173\u95ED:";

    // ========================================
    // Helper methods
    // ========================================

    private OmsOrder createOrder(Long id, Integer status) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setStatus(status);
        order.setDeleteStatus(0);
        order.setOrderSn("SN" + id);
        return order;
    }

    private OmsOrderDeliveryParam createDeliveryParam(Long orderId, String company, String sn) {
        OmsOrderDeliveryParam param = new OmsOrderDeliveryParam();
        param.setOrderId(orderId);
        param.setDeliveryCompany(company);
        param.setDeliverySn(sn);
        return param;
    }

    private OmsReceiverInfoParam createReceiverInfoParam(Long orderId, Integer status) {
        OmsReceiverInfoParam param = new OmsReceiverInfoParam();
        param.setOrderId(orderId);
        param.setReceiverName("\u5F20\u4E09"); // Zhang San
        param.setReceiverPhone("13800138000");
        param.setReceiverPostCode("518000");
        param.setReceiverDetailAddress("\u79D1\u6280\u56ED\u8DEF1\u53F7");
        param.setReceiverProvince("\u5E7F\u4E1C\u7701");
        param.setReceiverCity("\u6DF1\u5733\u5E02");
        param.setReceiverRegion("\u5357\u5C71\u533A");
        param.setStatus(status);
        return param;
    }

    private OmsMoneyInfoParam createMoneyInfoParam(Long orderId, Integer status) {
        OmsMoneyInfoParam param = new OmsMoneyInfoParam();
        param.setOrderId(orderId);
        param.setFreightAmount(new BigDecimal("15.00"));
        param.setDiscountAmount(new BigDecimal("5.00"));
        param.setStatus(status);
        return param;
    }

    // ========================================
    // Category 1: Normal flow (>= 2 cases)
    // ========================================

    @Nested
    @DisplayName("Normal flow")
    class NormalFlow {

        @Test
        @DisplayName("list: valid params returns orders")
        void testList_ValidParams_ReturnsOrders() {
            OmsOrderQueryParam queryParam = new OmsOrderQueryParam();
            queryParam.setStatus(1);

            List<OmsOrder> mockOrders = Arrays.asList(
                    createOrder(1L, 1),
                    createOrder(2L, 1)
            );
            when(orderDao.getList(any(OmsOrderQueryParam.class))).thenReturn(mockOrders);

            List<OmsOrder> result = orderService.list(queryParam, 10, 1);

            assertNotNull(result, "result must not be null");
            assertEquals(2, result.size(), "should return 2 orders");
            assertEquals(Long.valueOf(1L), result.get(0).getId());
            assertEquals(Integer.valueOf(1), result.get(0).getStatus());
            assertEquals(Long.valueOf(2L), result.get(1).getId());
        }

        @Test
        @DisplayName("detail: valid id returns order detail")
        void testDetail_ValidId_ReturnsOrderDetail() {
            Long orderId = 100L;
            OmsOrderDetail mockDetail = new OmsOrderDetail();
            mockDetail.setId(orderId);
            mockDetail.setStatus(0);
            mockDetail.setOrderSn("SN100");

            when(orderDao.getDetail(eq(orderId))).thenReturn(mockDetail);

            OmsOrderDetail result = orderService.detail(orderId);

            assertNotNull(result, "order detail must not be null");
            assertEquals(orderId, result.getId());
            assertEquals(Integer.valueOf(0), result.getStatus());
            assertEquals("SN100", result.getOrderSn());
        }

        @Test
        @DisplayName("delivery: updates status and creates history")
        void testDelivery_ValidParams_UpdatesStatusAndCreatesHistory() {
            List<OmsOrderDeliveryParam> params = Arrays.asList(
                    createDeliveryParam(1L, "SF", "SF123456"),
                    createDeliveryParam(2L, "YT", "YT789012")
            );
            when(orderDao.delivery(anyList())).thenReturn(2);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(2);

            int count = orderService.delivery(params);

            assertEquals(2, count, "should return delivery count=2");

            // Verify history content (business state assertions)
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(captor.capture());
            List<OmsOrderOperateHistory> histories = captor.getValue();
            assertEquals(2, histories.size(), "should create 2 history records");

            OmsOrderOperateHistory history1 = histories.get(0);
            assertEquals(Long.valueOf(1L), history1.getOrderId());
            assertEquals(Integer.valueOf(2), history1.getOrderStatus());
            assertEquals(NOTE_DELIVERY, history1.getNote());
            assertEquals(ADMIN_USER, history1.getOperateMan());
            assertNotNull(history1.getCreateTime());

            OmsOrderOperateHistory history2 = histories.get(1);
            assertEquals(Long.valueOf(2L), history2.getOrderId());
            assertEquals(NOTE_DELIVERY, history2.getNote());
        }

        @Test
        @DisplayName("close: closes orders and creates history")
        void testClose_ValidIds_ClosesOrdersAndCreatesHistory() {
            List<Long> ids = Arrays.asList(10L, 20L, 30L);
            String note = "timeout";
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(3);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(3);

            int count = orderService.close(ids, note);

            assertEquals(3, count, "should close 3 orders");

            // Verify order status set to 4 (closed)
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            ArgumentCaptor<OmsOrderExample> exampleCaptor = ArgumentCaptor.forClass(OmsOrderExample.class);
            verify(orderMapper).updateByExampleSelective(orderCaptor.capture(), exampleCaptor.capture());
            assertEquals(Integer.valueOf(4), orderCaptor.getValue().getStatus(),
                    "status should be set to 4 (closed)");

            // Verify history records
            ArgumentCaptor<List<OmsOrderOperateHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(historyCaptor.capture());
            List<OmsOrderOperateHistory> histories = historyCaptor.getValue();
            assertEquals(3, histories.size(), "should create 3 history records");

            assertEquals(Long.valueOf(10L), histories.get(0).getOrderId());
            assertEquals(Integer.valueOf(4), histories.get(0).getOrderStatus());
            assertEquals(CLOSE_PREFIX + note, histories.get(0).getNote());
            assertEquals(ADMIN_USER, histories.get(0).getOperateMan());

            assertEquals(Long.valueOf(30L), histories.get(2).getOrderId());
            assertEquals(CLOSE_PREFIX + note, histories.get(2).getNote());
        }

        @Test
        @DisplayName("delete: soft deletes orders")
        void testDelete_ValidIds_SoftDeletesOrders() {
            List<Long> ids = Arrays.asList(50L, 51L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(2);

            int count = orderService.delete(ids);

            assertEquals(2, count, "should delete 2 orders");

            // Verify deleteStatus is set to 1
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByExampleSelective(orderCaptor.capture(), any(OmsOrderExample.class));
            assertEquals(Integer.valueOf(1), orderCaptor.getValue().getDeleteStatus(),
                    "deleteStatus should be 1 (soft delete)");
            assertNull(orderCaptor.getValue().getStatus(),
                    "soft delete should not modify status field");
        }

        @Test
        @DisplayName("updateReceiverInfo: updates receiver info and creates history")
        void testUpdateReceiverInfo_ValidParam_UpdatesAndCreatesHistory() {
            OmsReceiverInfoParam param = createReceiverInfoParam(200L, 1);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateReceiverInfo(param);

            assertEquals(1, count, "should update 1 record");

            // Verify order fields
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder updatedOrder = orderCaptor.getValue();
            assertEquals(Long.valueOf(200L), updatedOrder.getId());
            assertEquals("\u5F20\u4E09", updatedOrder.getReceiverName());
            assertEquals("13800138000", updatedOrder.getReceiverPhone());
            assertNotNull(updatedOrder.getModifyTime());

            // Verify history
            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            OmsOrderOperateHistory history = historyCaptor.getValue();
            assertEquals(Long.valueOf(200L), history.getOrderId());
            assertEquals(Integer.valueOf(1), history.getOrderStatus());
            assertEquals(NOTE_RECEIVER, history.getNote());
            assertEquals(ADMIN_USER, history.getOperateMan());
        }

        @Test
        @DisplayName("updateMoneyInfo: updates money info and creates history")
        void testUpdateMoneyInfo_ValidParam_UpdatesAndCreatesHistory() {
            OmsMoneyInfoParam param = createMoneyInfoParam(300L, 0);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateMoneyInfo(param);

            assertEquals(1, count, "should update 1 record");

            // Verify money fields
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder order = orderCaptor.getValue();
            assertEquals(Long.valueOf(300L), order.getId());
            assertEquals(new BigDecimal("15.00"), order.getFreightAmount());
            assertEquals(new BigDecimal("5.00"), order.getDiscountAmount());

            // Verify history
            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            assertEquals(Long.valueOf(300L), historyCaptor.getValue().getOrderId());
            assertEquals(Integer.valueOf(0), historyCaptor.getValue().getOrderStatus());
            assertEquals(NOTE_MONEY, historyCaptor.getValue().getNote());
        }

        @Test
        @DisplayName("updateNote: updates note and creates history")
        void testUpdateNote_ValidParams_UpdatesAndCreatesHistory() {
            Long orderId = 400L;
            String note = "rush order";
            Integer status = 1;
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateNote(orderId, note, status);

            assertEquals(1, count, "should update 1 record");

            // Verify note field
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder order = orderCaptor.getValue();
            assertEquals(orderId, order.getId());
            assertEquals(note, order.getNote());
            assertNotNull(order.getModifyTime());

            // Verify history
            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            OmsOrderOperateHistory history = historyCaptor.getValue();
            assertEquals(orderId, history.getOrderId());
            assertEquals(status, history.getOrderStatus());
            assertEquals(NOTE_PREFIX + note, history.getNote());
            assertEquals(ADMIN_USER, history.getOperateMan());
        }
    }

    // ========================================
    // Category 2: Boundary tests (>= 2 cases)
    // ========================================

    @Nested
    @DisplayName("Boundary")
    class Boundary {

        @Test
        @DisplayName("close: empty id list - graceful handling")
        void testClose_EmptyIdList_UpdatesZeroRecords() {
            List<Long> emptyIds = Collections.emptyList();
            String note = "test";
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(0);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(0);

            int count = orderService.close(emptyIds, note);

            assertEquals(0, count, "empty list should return 0");
            // Verify empty history list is still created
            verify(orderOperateHistoryDao).insertList(argThat(list -> {
                if (list == null) return false;
                @SuppressWarnings("unchecked")
                List<OmsOrderOperateHistory> hList = (List<OmsOrderOperateHistory>) list;
                return hList.isEmpty();
            }));
        }

        @Test
        @DisplayName("delivery: empty param list - graceful handling")
        void testDelivery_EmptyParamList_ReturnsZero() {
            List<OmsOrderDeliveryParam> emptyParams = Collections.emptyList();
            when(orderDao.delivery(anyList())).thenReturn(0);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(0);

            int count = orderService.delivery(emptyParams);

            assertEquals(0, count, "empty delivery list should return 0");
            verify(orderOperateHistoryDao).insertList(argThat(list -> {
                @SuppressWarnings("unchecked")
                List<OmsOrderOperateHistory> hList = (List<OmsOrderOperateHistory>) list;
                return hList.isEmpty();
            }));
        }

        @Test
        @DisplayName("list: null query param - handles gracefully")
        void testList_NullQueryParam_HandlesGracefully() {
            when(orderDao.getList(isNull())).thenReturn(Collections.emptyList());

            List<OmsOrder> result = orderService.list(null, 10, 1);

            assertNotNull(result, "null param should return non-null result");
            assertTrue(result.isEmpty(), "null param should return empty list");
        }

        @Test
        @DisplayName("detail: null id - returns null")
        void testDetail_NullId_ReturnsNull() {
            when(orderDao.getDetail(isNull())).thenReturn(null);

            OmsOrderDetail result = orderService.detail(null);

            assertNull(result, "null id should return null");
        }

        @Test
        @DisplayName("delete: empty id list - graceful handling")
        void testDelete_EmptyIdList_UpdatesZeroRecords() {
            List<Long> emptyIds = Collections.emptyList();
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(0);

            int count = orderService.delete(emptyIds);

            assertEquals(0, count, "empty list should return 0");

            // Verify deleteStatus is set to 1 even for empty list
            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByExampleSelective(captor.capture(), any(OmsOrderExample.class));
            assertEquals(Integer.valueOf(1), captor.getValue().getDeleteStatus(),
                    "deleteStatus should be 1 even for empty list");
        }

        @Test
        @DisplayName("updateNote: empty string note")
        void testUpdateNote_EmptyNote_CreatesHistoryWithEmptyConcat() {
            Long orderId = 500L;
            String note = "";
            Integer status = 2;
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateNote(orderId, note, status);

            assertEquals(1, count, "should update 1 record");

            // Verify history note = prefix + ""
            ArgumentCaptor<OmsOrderOperateHistory> captor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(captor.capture());
            assertEquals(NOTE_PREFIX, captor.getValue().getNote(),
                    "empty note should produce prefix only");
        }

        @Test
        @DisplayName("close: null note - handles gracefully")
        void testClose_NullNote_ClosesOrders() {
            List<Long> ids = Arrays.asList(60L);
            String note = null;
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(1);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(1);

            int count = orderService.close(ids, note);

            assertEquals(1, count, "should close 1 order");

            // Verify history note = "prefix" + null
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(captor.capture());
            @SuppressWarnings("unchecked")
            List<OmsOrderOperateHistory> histories = captor.getValue();
            assertEquals(CLOSE_PREFIX + null, histories.get(0).getNote(),
                    "null note should produce 'prefix' + null");
        }
    }

    // ========================================
    // Category 3: Exception branch (>= 2 cases)
    // ========================================

    @Nested
    @DisplayName("Exception branch")
    class ExceptionBranch {

        @Test
        @DisplayName("delivery: dao throws exception - propagates without creating history")
        void testDelivery_DaoThrowsException_PropagatesException() {
            List<OmsOrderDeliveryParam> params = Arrays.asList(
                    createDeliveryParam(1L, "SF", "SF999")
            );
            when(orderDao.delivery(anyList()))
                    .thenThrow(new RuntimeException("DB connection failed"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> orderService.delivery(params),
                    "DAO exception should propagate");
            assertEquals("DB connection failed", ex.getMessage());

            // Verify history is NOT created (transactional guarantee)
            verify(orderOperateHistoryDao, never()).insertList(anyList());
        }

        @Test
        @DisplayName("close: mapper exception - propagates without creating history")
        void testClose_MapperThrowsException_PropagatesAndNoHistoryCreated() {
            List<Long> ids = Arrays.asList(70L, 71L);
            String note = "close-exception";
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class)))
                    .thenThrow(new RuntimeException("Update failed"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> orderService.close(ids, note),
                    "Mapper exception should propagate");
            assertEquals("Update failed", ex.getMessage());

            // Verify history not created
            verify(orderOperateHistoryDao, never()).insertList(anyList());
        }

        @Test
        @DisplayName("updateNote: mapper throws exception - propagates without creating history")
        void testUpdateNote_MapperThrowsException_PropagatesAndNoHistoryCreated() {
            Long orderId = 800L;
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class)))
                    .thenThrow(new RuntimeException("DB timeout"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> orderService.updateNote(orderId, "test", 1),
                    "Mapper exception should propagate");
            assertEquals("DB timeout", ex.getMessage());

            // Verify history not created
            verify(orderOperateHistoryMapper, never()).insert(any(OmsOrderOperateHistory.class));
        }

        @Test
        @DisplayName("updateReceiverInfo: mapper exception - propagates without creating history")
        void testUpdateReceiverInfo_MapperThrowsException_PropagatesAndNoHistoryCreated() {
            OmsReceiverInfoParam param = createReceiverInfoParam(900L, 1);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class)))
                    .thenThrow(new RuntimeException("Update receiver info failed"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> orderService.updateReceiverInfo(param),
                    "Mapper exception should propagate");
            assertEquals("Update receiver info failed", ex.getMessage());

            // Verify history not created
            verify(orderOperateHistoryMapper, never()).insert(any(OmsOrderOperateHistory.class));
        }

        @Test
        @DisplayName("updateMoneyInfo: history insert exception - propagates")
        void testUpdateMoneyInfo_HistoryInsertThrowsException_PropagatesException() {
            OmsMoneyInfoParam param = createMoneyInfoParam(777L, 2);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class)))
                    .thenThrow(new RuntimeException("History insert failed"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> orderService.updateMoneyInfo(param),
                    "History insert exception should propagate");
            assertEquals("History insert failed", ex.getMessage());
        }
    }

    // ========================================
    // Category 4: Concurrency tests (>= 2 cases)
    // ========================================

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("updateNote: concurrent updates on same order - all succeed")
        void testUpdateNote_ConcurrentCallsOnSameOrder_AllSucceed() throws InterruptedException {
            final Long orderId = 1000L;
            final int threadCount = 5;
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final String note = "thread-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int result = orderService.updateNote(orderId, note, 3);
                        if (result == 1) {
                            successCount.incrementAndGet();
                        }
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

            assertTrue(finished, "all threads should complete within timeout");
            assertEquals(threadCount, successCount.get(),
                    "all " + threadCount + " threads should succeed");
            assertEquals(0, failCount.get(), "no thread should fail");

            // Verify total invocations
            verify(orderMapper, times(threadCount)).updateByPrimaryKeySelective(any(OmsOrder.class));
            verify(orderOperateHistoryMapper, times(threadCount)).insert(any(OmsOrderOperateHistory.class));

            // Verify each history record
            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper, times(threadCount)).insert(historyCaptor.capture());
            Set<String> notes = new HashSet<>();
            for (OmsOrderOperateHistory h : historyCaptor.getAllValues()) {
                assertEquals(orderId, h.getOrderId());
                assertEquals(Integer.valueOf(3), h.getOrderStatus());
                assertTrue(h.getNote().startsWith(NOTE_PREFIX), "note should start with prefix");
                notes.add(h.getNote());
            }
            assertEquals(threadCount, notes.size(), "each thread should have a distinct note");
        }

        @Test
        @DisplayName("close: concurrent close on different orders - no cross interference")
        void testClose_ConcurrentCallsOnDifferentOrders_NoCrossInterference() throws InterruptedException {
            final int threadCount = 4;
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(1);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(1);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger totalClosed = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final List<Long> ids = Collections.singletonList((long) (2000 + i));
                final String note = "close-batch-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int result = orderService.close(ids, note);
                        totalClosed.addAndGet(result);
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

            assertTrue(finished, "all threads should complete within timeout");
            assertEquals(threadCount, totalClosed.get(),
                    "total should be " + threadCount + " closed orders");

            verify(orderMapper, times(threadCount))
                    .updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class));
            verify(orderOperateHistoryDao, times(threadCount)).insertList(anyList());

            // Verify each close operation sets status to 4
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper, times(threadCount))
                    .updateByExampleSelective(orderCaptor.capture(), any(OmsOrderExample.class));
            for (OmsOrder captured : orderCaptor.getAllValues()) {
                assertEquals(Integer.valueOf(4), captured.getStatus(),
                        "each close operation should set status to 4");
            }
        }

        @Test
        @DisplayName("delivery: concurrent delivery on different orders - no cross interference")
        void testDelivery_ConcurrentCallsOnDifferentOrders_NoCrossInterference() throws InterruptedException {
            final int threadCount = 3;
            when(orderDao.delivery(anyList())).thenReturn(1);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(1);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger totalDelivered = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final List<OmsOrderDeliveryParam> params = Arrays.asList(
                        createDeliveryParam((long) (3000 + i), "Logistics" + i, "SN" + i)
                );
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int result = orderService.delivery(params);
                        totalDelivered.addAndGet(result);
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

            assertTrue(finished, "all threads should complete within timeout");
            assertEquals(threadCount, totalDelivered.get(),
                    "total should be " + threadCount + " delivered orders");

            verify(orderDao, times(threadCount)).delivery(anyList());
            verify(orderOperateHistoryDao, times(threadCount)).insertList(anyList());

            // Verify all history records have correct status (2 = delivered)
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao, times(threadCount)).insertList(captor.capture());
            for (List<OmsOrderOperateHistory> histories : captor.getAllValues()) {
                for (OmsOrderOperateHistory h : histories) {
                    assertEquals(Integer.valueOf(2), h.getOrderStatus(),
                            "delivery history status must be 2 (delivered)");
                    assertEquals(NOTE_DELIVERY, h.getNote(),
                            "delivery history note must be correct");
                    assertEquals(ADMIN_USER, h.getOperateMan(),
                            "operator must be admin");
                    assertNotNull(h.getCreateTime(), "create time must not be null");
                }
            }
        }
    }
}
