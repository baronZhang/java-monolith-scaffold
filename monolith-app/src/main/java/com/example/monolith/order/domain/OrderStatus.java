package com.example.monolith.order.domain;

/**
 * 订单状态。
 *
 * <p>和微服务版对比：微服务版多一个 RESERVING（远程预占库存中）。单体里预占库存和写订单在同一个本地事务里，
 * 外界看不到"预占了一半"的中间状态，所以不需要它。
 */
public enum OrderStatus {
    /** 待支付 */
    PENDING_PAYMENT("待支付"),
    /** 已支付 */
    PAID("已支付"),
    /** 已取消 */
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
