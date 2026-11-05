package com.example.monolith.shared.api;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果，对应前端的 {@code PageResult<T>}。
 *
 * @param list 当前页数据
 * @param total 符合条件的总条数
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 */
public record PageResult<T>(List<T> list, long total, long page, long pageSize) {

    public PageResult {
        list = List.copyOf(list);
    }

    /** 把每一条数据换成另一种类型（比如领域对象 → 视图），分页信息不变。 */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(list.stream().<R>map(mapper).toList(), total, page, pageSize);
    }

    /**
     * 规范化分页参数：和 Vue Mock 的行为一致，页码至少为 1，每页 1~100 条，非法值不报错而是纠正。
     * 每页条数一定要有上限：否则一个 pageSize=1000000 的请求就能把数据库和内存打满。
     */
    public static long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
