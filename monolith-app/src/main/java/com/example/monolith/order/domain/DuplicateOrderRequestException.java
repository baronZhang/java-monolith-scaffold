package com.example.monolith.order.domain;

/**
 * 同一个用户用同一个幂等键重复下单：数据库唯一索引 (user_id, idempotency_key) 拦下了第二次插入。
 *
 * <p>由仓储适配器把框架的 DuplicateKeyException 翻译成这个领域异常，应用层不用认识持久化框架的异常类型。
 */
public class DuplicateOrderRequestException extends RuntimeException {

    public DuplicateOrderRequestException(String idempotencyKey, Throwable cause) {
        super("幂等键重复：" + idempotencyKey, cause);
    }
}
