package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单超时关闭请求参数
 * Created by macro on 2024/7/28.
 */
@Getter
@Setter
public class OmsOrderTimeoutCloseParam {
    @NotNull(message = "订单ID不能为空")
    @Schema(title = "订单ID")
    private Long orderId;

    @Schema(title = "触发来源", description = "如: SCHEDULE-定时任务, MQ-消息队列")
    private String source;

    @Schema(title = "操作备注", description = "如: 超时未支付系统自动关闭")
    private String note;
}
