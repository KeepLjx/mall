package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.OmsOrderDao;
import com.macro.mall.dao.OmsOrderOperateHistoryDao;
import com.macro.mall.dto.*;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderOperateHistoryMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderOperateHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OmsOrderServiceImpl Unit Test")
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

    // === helpers ===

    private OmsOrderDeliveryParam buildDeliveryParam(Long orderId, String company, String sn) {
        OmsOrderDeliveryParam param = new OmsOrderDeliveryParam();
        param.setOrderId(orderId);
        param.setDeliveryCompany(company);
        param.setDeliverySn(sn);
        return param;
    }

    private OmsReceiverInfoParam buildReceiverInfoParam(Long orderId) {
        OmsReceiverInfoParam param = new OmsReceiverInfoParam();
        param.setOrderId(orderId);
        param.setReceiverName("Zhang San");
        param.setReceiverPhone("13800138000");
        param.setReceiverPostCode("100000");
        param.setReceiverDetailAddress("1 Keji Road");
        param.setReceiverProvince("Guangdong");
        param.setReceiverCity("Shenzhen");
        param.setReceiverRegion("Nanshan");
        param.setStatus(2);
        return param;
    }

    private OmsMoneyInfoParam buildMoneyInfoParam(Long orderId) {
        OmsMoneyInfoParam param = new OmsMoneyInfoParam();
        param.setOrderId(orderId);
        param.setFreightAmount(new BigDecimal("10.00"));
        param.setDiscountAmount(new BigDecimal("5.00"));
        param.setStatus(2);
        return param;
    }

    // === Normal: delivery ===

    @Nested
    @DisplayName("delivery - normal")
    class Delivery_Normal {

        @Test
        @DisplayName("normal: batch deliver 2 orders, verify history business state")
        void shouldDeliverOrdersAndRecordHistory() {
            OmsOrderDeliveryParam p1 = buildDeliveryParam(1L, "SF", "SF123");
            OmsOrderDeliveryParam p2 = buildDeliveryParam(2L, "YT", "YT456");
            List<OmsOrderDeliveryParam> params = Arrays.asList(p1, p2);

            when(orderDao.delivery(params)).thenReturn(2);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(2);

            int count = orderService.delivery(params);

            assertEquals(2, count);
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(captor.capture());
            List<OmsOrderOperateHistory> histories = captor.getValue();
            assertEquals(2, histories.size());

            assertEquals(1L, histories.get(0).getOrderId());
            assertNotNull(histories.get(0).getOperateMan());
            assertFalse(histories.get(0).getOperateMan().isEmpty());
            assertEquals(2, histories.get(0).getOrderStatus());
            assertNotNull(histories.get(0).getNote());
            assertFalse(histories.get(0).getNote().isEmpty());
            assertNotNull(histories.get(0).getCreateTime());

            assertEquals(2L, histories.get(1).getOrderId());
            assertEquals(2, histories.get(1).getOrderStatus());
            assertNotNull(histories.get(1).getNote());
        }

        @Test
        @DisplayName("normal: deliver single order")
        void shouldDeliverSingleOrder() {
            OmsOrderDeliveryParam p1 = buildDeliveryParam(100L, "JD", "JD001");
            List<OmsOrderDeliveryParam> params = Collections.singletonList(p1);

            when(orderDao.delivery(params)).thenReturn(1);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(1);

            int count = orderService.delivery(params);

            assertEquals(1, count);
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(captor.capture());
            assertEquals(1, captor.getValue().size());
            assertEquals(100L, captor.getValue().get(0).getOrderId());
            assertEquals(2, captor.getValue().get(0).getOrderStatus());
        }
    }

    // === Normal: close ===

    @Nested
    @DisplayName("close - normal")
    class Close_Normal {

        @Test
        @DisplayName("normal: batch close 2 orders, verify status=4 and history")
        void shouldCloseOrdersAndRecordHistory() {
            List<Long> ids = Arrays.asList(1L, 2L);
            String note = "customer cancel";

            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(2);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(2);

            int count = orderService.close(ids, note);

            assertEquals(2, count);

            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByExampleSelective(orderCaptor.capture(), any(OmsOrderExample.class));
            assertEquals(4, orderCaptor.getValue().getStatus());

            ArgumentCaptor<List<OmsOrderOperateHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(historyCaptor.capture());
            List<OmsOrderOperateHistory> histories = historyCaptor.getValue();
            assertEquals(2, histories.size());

            assertEquals(1L, histories.get(0).getOrderId());
            assertNotNull(histories.get(0).getOperateMan());
            assertFalse(histories.get(0).getOperateMan().isEmpty());
            assertEquals(4, histories.get(0).getOrderStatus());
            assertNotNull(histories.get(0).getNote());
            assertTrue(histories.get(0).getNote().contains(note));
            assertNotNull(histories.get(0).getCreateTime());

            assertEquals(2L, histories.get(1).getOrderId());
            assertEquals(4, histories.get(1).getOrderStatus());
        }

        @Test
        @DisplayName("normal: close single order with empty note")
        void shouldCloseSingleOrderWithEmptyNote() {
            List<Long> ids = Collections.singletonList(10L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(1);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(1);

            int count = orderService.close(ids, "");

            assertEquals(1, count);
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(captor.capture());
            assertNotNull(captor.getValue().get(0).getNote());
        }
    }

    // === Boundary ===

    @Nested
    @DisplayName("boundary")
    class Boundary {

        @Test
        @DisplayName("boundary: delivery with empty list returns 0")
        void deliveryWithEmptyListShouldReturnZero() {
            when(orderDao.delivery(Collections.emptyList())).thenReturn(0);

            int count = orderService.delivery(Collections.emptyList());

            assertEquals(0, count);
            verify(orderOperateHistoryDao).insertList(anyList());
        }

        @Test
        @DisplayName("boundary: close with empty ids returns 0")
        void closeWithEmptyIdsShouldReturnZero() {
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(0);

            int count = orderService.close(Collections.emptyList(), "test");

            assertEquals(0, count);
        }

        @Test
        @DisplayName("boundary: delete with empty ids returns 0, deleteStatus=1")
        void deleteWithEmptyIdsShouldReturnZero() {
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(0);

            int count = orderService.delete(Collections.emptyList());

            assertEquals(0, count);
            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByExampleSelective(captor.capture(), any(OmsOrderExample.class));
            assertEquals(1, captor.getValue().getDeleteStatus());
        }

        @Test
        @DisplayName("boundary: updateNote with null status")
        void updateNoteWithNullStatus() {
            Long orderId = 1L;
            String note = "test note";
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateNote(orderId, note, null);

            assertEquals(1, count);
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            assertEquals(orderId, orderCaptor.getValue().getId());
            assertEquals(note, orderCaptor.getValue().getNote());
            assertNotNull(orderCaptor.getValue().getModifyTime());

            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            assertEquals(orderId, historyCaptor.getValue().getOrderId());
            assertNull(historyCaptor.getValue().getOrderStatus());
            assertNotNull(historyCaptor.getValue().getNote());
            assertTrue(historyCaptor.getValue().getNote().contains(note));
        }
    }

    // === Exception ===

    @Nested
    @DisplayName("exception")
    class ExceptionTests {

        @Test
        @DisplayName("exception: close returns 0 when no orders match, history still recorded")
        void closeWhenNoOrdersUpdated() {
            List<Long> ids = Arrays.asList(999L, 888L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(0);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(2);

            int count = orderService.close(ids, "close nonexistent");

            assertEquals(0, count);
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao).insertList(captor.capture());
            assertEquals(2, captor.getValue().size());
            assertEquals(999L, captor.getValue().get(0).getOrderId());
            assertEquals(4, captor.getValue().get(0).getOrderStatus());
        }

        @Test
        @DisplayName("exception: delete returns 0 when no orders match, deleteStatus=1 set")
        void deleteWhenNoOrdersUpdated() {
            List<Long> ids = Arrays.asList(999L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(0);

            int count = orderService.delete(ids);

            assertEquals(0, count);
            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByExampleSelective(captor.capture(), any(OmsOrderExample.class));
            assertEquals(1, captor.getValue().getDeleteStatus());
        }

        @Test
        @DisplayName("exception: detail with null id returns null")
        void detailWithNullId() {
            when(orderDao.getDetail(null)).thenReturn(null);

            OmsOrderDetail result = orderService.detail(null);

            assertNull(result);
        }

        @Test
        @DisplayName("exception: updateReceiverInfo returns 0, history still inserted")
        void updateReceiverInfoWhenOrderNotExists() {
            OmsReceiverInfoParam param = buildReceiverInfoParam(999L);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(0);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateReceiverInfo(param);

            assertEquals(0, count);
            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            assertEquals(999L, historyCaptor.getValue().getOrderId());
            assertEquals(2, historyCaptor.getValue().getOrderStatus());
            assertNotNull(historyCaptor.getValue().getNote());
            assertNotNull(historyCaptor.getValue().getOperateMan());
        }
    }

    // === Other normal ===

    @Nested
    @DisplayName("other methods - normal")
    class OtherMethods_Normal {

        @Test
        @DisplayName("normal: list paginated orders")
        void shouldListOrdersWithPagination() {
            try (MockedStatic<PageHelper> pageHelperMock = mockStatic(PageHelper.class)) {
                OmsOrderQueryParam queryParam = new OmsOrderQueryParam();
                queryParam.setStatus(0);

                OmsOrder order1 = new OmsOrder();
                order1.setId(1L);
                order1.setOrderSn("ORD001");
                order1.setStatus(0);
                OmsOrder order2 = new OmsOrder();
                order2.setId(2L);
                order2.setOrderSn("ORD002");
                order2.setStatus(0);

                when(orderDao.getList(queryParam)).thenReturn(Arrays.asList(order1, order2));

                List<OmsOrder> result = orderService.list(queryParam, 10, 1);

                assertNotNull(result);
                assertEquals(2, result.size());
                assertEquals(1L, result.get(0).getId());
                assertEquals("ORD001", result.get(0).getOrderSn());
                assertEquals(0, result.get(0).getStatus());
                assertEquals(2L, result.get(1).getId());
                assertEquals("ORD002", result.get(1).getOrderSn());
                assertEquals(0, result.get(1).getStatus());
                verify(orderDao).getList(queryParam);
            }
        }

        @Test
        @DisplayName("normal: detail by id returns order detail")
        void shouldReturnOrderDetailById() {
            Long orderId = 1L;
            OmsOrderDetail expectedDetail = new OmsOrderDetail();
            expectedDetail.setId(orderId);
            expectedDetail.setOrderSn("ORD001");
            expectedDetail.setStatus(1);

            when(orderDao.getDetail(orderId)).thenReturn(expectedDetail);

            OmsOrderDetail result = orderService.detail(orderId);

            assertNotNull(result);
            assertEquals(orderId, result.getId());
            assertEquals("ORD001", result.getOrderSn());
            assertEquals(1, result.getStatus());
            verify(orderDao).getDetail(orderId);
        }

        @Test
        @DisplayName("normal: delete batch orders, verify deleteStatus=1 (soft delete)")
        void shouldDeleteOrdersWithSoftDelete() {
            List<Long> ids = Arrays.asList(1L, 2L, 3L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(3);

            int count = orderService.delete(ids);

            assertEquals(3, count);
            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByExampleSelective(captor.capture(), any(OmsOrderExample.class));
            assertEquals(1, captor.getValue().getDeleteStatus());
            assertNull(captor.getValue().getId());
        }

        @Test
        @DisplayName("normal: updateReceiverInfo, verify field mapping")
        void shouldUpdateReceiverInfoCorrectly() {
            OmsReceiverInfoParam param = buildReceiverInfoParam(10L);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateReceiverInfo(param);

            assertEquals(1, count);
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder capturedOrder = orderCaptor.getValue();
            assertEquals(10L, capturedOrder.getId());
            assertEquals("Zhang San", capturedOrder.getReceiverName());
            assertEquals("13800138000", capturedOrder.getReceiverPhone());
            assertEquals("100000", capturedOrder.getReceiverPostCode());
            assertEquals("1 Keji Road", capturedOrder.getReceiverDetailAddress());
            assertEquals("Guangdong", capturedOrder.getReceiverProvince());
            assertEquals("Shenzhen", capturedOrder.getReceiverCity());
            assertEquals("Nanshan", capturedOrder.getReceiverRegion());
            assertNotNull(capturedOrder.getModifyTime());

            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            assertEquals(10L, historyCaptor.getValue().getOrderId());
            assertEquals(2, historyCaptor.getValue().getOrderStatus());
            assertNotNull(historyCaptor.getValue().getNote());
        }

        @Test
        @DisplayName("normal: updateMoneyInfo, verify amount field mapping")
        void shouldUpdateMoneyInfoCorrectly() {
            OmsMoneyInfoParam param = buildMoneyInfoParam(20L);
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateMoneyInfo(param);

            assertEquals(1, count);
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            OmsOrder capturedOrder = orderCaptor.getValue();
            assertEquals(20L, capturedOrder.getId());
            assertEquals(new BigDecimal("10.00"), capturedOrder.getFreightAmount());
            assertEquals(new BigDecimal("5.00"), capturedOrder.getDiscountAmount());
            assertNotNull(capturedOrder.getModifyTime());

            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            assertEquals(20L, historyCaptor.getValue().getOrderId());
            assertEquals(2, historyCaptor.getValue().getOrderStatus());
            assertNotNull(historyCaptor.getValue().getNote());
        }

        @Test
        @DisplayName("normal: updateNote")
        void shouldUpdateNoteCorrectly() {
            Long orderId = 30L;
            String note = "rush order";
            Integer status = 1;
            when(orderMapper.updateByPrimaryKeySelective(any(OmsOrder.class))).thenReturn(1);
            when(orderOperateHistoryMapper.insert(any(OmsOrderOperateHistory.class))).thenReturn(1);

            int count = orderService.updateNote(orderId, note, status);

            assertEquals(1, count);
            ArgumentCaptor<OmsOrder> orderCaptor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper).updateByPrimaryKeySelective(orderCaptor.capture());
            assertEquals(orderId, orderCaptor.getValue().getId());
            assertEquals(note, orderCaptor.getValue().getNote());
            assertNotNull(orderCaptor.getValue().getModifyTime());

            ArgumentCaptor<OmsOrderOperateHistory> historyCaptor = ArgumentCaptor.forClass(OmsOrderOperateHistory.class);
            verify(orderOperateHistoryMapper).insert(historyCaptor.capture());
            assertEquals(orderId, historyCaptor.getValue().getOrderId());
            assertEquals(Integer.valueOf(1), historyCaptor.getValue().getOrderStatus());
            assertNotNull(historyCaptor.getValue().getNote());
            assertTrue(historyCaptor.getValue().getNote().contains(note));
            assertNotNull(historyCaptor.getValue().getOperateMan());
        }
    }

    // === Concurrent ===

    @Nested
    @DisplayName("concurrent")
    class ConcurrentTests {

        @Test
        @DisplayName("concurrent: multi-thread close same orders, all succeed, history recorded")
        void concurrentCloseShouldBeThreadSafe() throws Exception {
            List<Long> ids = Arrays.asList(1L, 2L);
            when(orderMapper.updateByExampleSelective(any(OmsOrder.class), any(OmsOrderExample.class))).thenReturn(2);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(2);

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        orderService.close(ids, "concurrent close");
                        successCount.incrementAndGet();
                    } catch (RuntimeException e) {
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
            ArgumentCaptor<OmsOrder> captor = ArgumentCaptor.forClass(OmsOrder.class);
            verify(orderMapper, atLeastOnce()).updateByExampleSelective(captor.capture(), any(OmsOrderExample.class));
            assertEquals(4, captor.getValue().getStatus());
            verify(orderOperateHistoryDao, atLeastOnce()).insertList(anyList());
        }

        @Test
        @DisplayName("concurrent: multi-thread delivery same orders, all succeed, history complete")
        void concurrentDeliveryShouldBeThreadSafe() throws Exception {
            OmsOrderDeliveryParam p1 = buildDeliveryParam(1L, "SF", "SF001");
            OmsOrderDeliveryParam p2 = buildDeliveryParam(2L, "YT", "YT002");
            List<OmsOrderDeliveryParam> params = Arrays.asList(p1, p2);

            when(orderDao.delivery(params)).thenReturn(2);
            when(orderOperateHistoryDao.insertList(anyList())).thenReturn(2);

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        int c = orderService.delivery(params);
                        assertEquals(2, c);
                        successCount.incrementAndGet();
                    } catch (Throwable e) {
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
            ArgumentCaptor<List<OmsOrderOperateHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(orderOperateHistoryDao, atLeastOnce()).insertList(captor.capture());
            List<OmsOrderOperateHistory> capturedHistories = captor.getValue();
            assertFalse(capturedHistories.isEmpty());
            assertEquals(2, capturedHistories.get(0).getOrderStatus());
            assertNotNull(capturedHistories.get(0).getNote());
            assertFalse(capturedHistories.get(0).getNote().isEmpty());
            assertNotNull(capturedHistories.get(0).getOperateMan());
        }
    }
}
