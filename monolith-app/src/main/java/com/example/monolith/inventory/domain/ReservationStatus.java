package com.example.monolith.inventory.domain;

/**
 * 预占记录的状态。状态只能单向流转：RESERVED → CONFIRMED 或 RESERVED → RELEASED。
 * 事件重复投递时，条件更新 "WHERE status = 'RESERVED'" 只会成功一次，这就是监听器幂等的依据。
 */
public enum ReservationStatus {
    /** 已预占，等待支付 */
    RESERVED,
    /** 已支付，预占转为已售 */
    CONFIRMED,
    /** 已取消，预占退回可售 */
    RELEASED
}
