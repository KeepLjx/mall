package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OrderParam;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 鍓嶅彴璁㈠崟绠＄悊Service
 * Created by macro on 2018/8/30.
 */
public interface OmsPortalOrderService {
    /**
     * 鏍规嵁鐢ㄦ埛璐墿杞︿俊鎭敓鎴愮‘璁ゅ崟淇℃伅
     */
    ConfirmOrderResult generateConfirmOrder(List<Long> cartIds);

    /**
     * 鏍规嵁鎻愪氦淇℃伅鐢熸垚璁㈠崟
     */
    @Transactional
    Map<String, Object> generateOrder(OrderParam orderParam);

    /**
     * 鏀粯鎴愬姛鍚庣殑鍥炶皟
     */
    @Transactional
    Integer paySuccess(Long orderId, Integer payType);

    /**
     * 鑷姩鍙栨秷瓒呮椂璁㈠崟
     */
    @Transactional
    Integer cancelTimeOutOrder();

    /**
     * 鍙栨秷鍗曚釜瓒呮椂璁㈠崟
     */
    @Transactional
    void cancelOrder(Long orderId);

    /**
     * 鍙戦�佸欢杩熸秷鎭彇娑堣鍗?
     */
    void sendDelayMessageCancelOrder(Long orderId);

    /**
     * 纭鏀惰揣
     */
    void confirmReceiveOrder(Long orderId);

    /**
     * 鍒嗛〉鑾峰彇鐢ㄦ埛璁㈠崟
     */
    CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize);

    /**
     * 鏍规嵁璁㈠崟ID鑾峰彇璁㈠崟璇︽儏
     */
    OmsOrderDetail detail(Long orderId);

    /**
     * 鐢ㄦ埛鏍规嵁璁㈠崟ID鍒犻櫎璁㈠崟
     */
    void deleteOrder(Long orderId);

    /**
     * 鏍规嵁orderSn鏉ュ疄鐜扮殑鏀粯鎴愬姛閫昏緫
     */
    @Transactional
    void paySuccessByOrderSn(String orderSn, Integer payType);

    /**
     * 超时订单自动关闭（使用CAS原子更新防并发重复关闭）
     * @return 影响行数，>0 表示关闭成功，0 表示订单已被关闭或状态不允许关闭
     */
    @Transactional
    Integer closeTimeoutOrder(Long orderId);
}
