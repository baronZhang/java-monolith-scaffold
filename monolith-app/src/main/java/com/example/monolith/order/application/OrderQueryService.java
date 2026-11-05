package com.example.monolith.order.application;

import com.example.monolith.order.domain.OrderRepository;
import com.example.monolith.shared.api.PageResult;
import com.example.monolith.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单查询，带数据权限：普通用户只能看自己的订单。
 *
 * <p>功能权限（能不能调这个接口）和数据权限（能看哪些数据）是两回事。只做功能权限，
 * 用户把 URL 里的订单号改成别人的，就能看到别人的订单——这就是越权漏洞（IDOR）。
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orders;
    private final OrderProperties properties;

    public OrderQueryService(OrderRepository orders, OrderProperties properties) {
        this.orders = orders;
        this.properties = properties;
    }

    /**
     * 查看订单详情。
     *
     * <p>看别人的订单时返回 404 而不是 403：403 等于告诉对方"这个订单号存在"，可以被用来枚举订单号。
     *
     * @param canViewAll 是否拥有查看全部订单的权限（order:manage）
     */
    public OrderView get(long orderNo, long viewerId, boolean canViewAll) {
        return orders.findByOrderNo(orderNo)
                .filter(order -> canViewAll || order.isOwnedBy(viewerId))
                .map(order -> OrderView.from(order, properties.paymentTimeout()))
                .orElseThrow(() -> BusinessException.notFound("订单 " + orderNo + " 不存在"));
    }

    public PageResult<OrderView> listMine(long userId, Long page, Long pageSize) {
        return orders.findByUser(userId, PageResult.normalizePage(page), PageResult.normalizePageSize(pageSize))
                .map(order -> OrderView.from(order, properties.paymentTimeout()));
    }
}
