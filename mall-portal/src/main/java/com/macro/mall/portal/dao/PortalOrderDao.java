package com.macro.mall.portal.dao;

import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 鍓嶅彴璁㈠崟绠＄悊鑷畾涔塂ao
 * Created by macro on 2018/9/4.
 */
public interface PortalOrderDao {
    /**
     * 鑾峰彇璁㈠崟鍙婁笅鍗曞晢鍝佽鎯?
     */
    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    /**
     * 淇敼 pms_sku_stock琛ㄧ殑閿佸畾搴撳瓨鍙婄湡瀹炲簱瀛?
     */
    int updateSkuStock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /**
     * 鑾峰彇瓒呮椂璁㈠崟
     * @param minute 瓒呮椂鏃堕棿锛堝垎锛?
     */
    List<OmsOrderDetail> getTimeOutOrders(@Param("minute") Integer minute);

    /**
     * 鎵归噺淇敼璁㈠崟鐘舵�?
     */
    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    /**
     * 瑙ｉ櫎鍙栨秷璁㈠崟鐨勫簱瀛橀攣瀹?
     */
    int releaseSkuStockLock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /**
     * 使用CAS原子更新关闭超时订单（仅当状态为待付款时关闭）
     * @return 影响行数，>0 表示关闭成功，0 表示已被其他线程关闭或订单状态已变更
     */
    int closeTimeoutOrder(@Param("orderId") Long orderId);

}
