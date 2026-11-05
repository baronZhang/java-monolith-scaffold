package com.example.monolith.order.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 下单命令。
 *
 * @param userId 下单用户（来自登录态，不信任请求体里传来的用户 id）
 * @param idempotencyKey 幂等键：客户端为"这一次下单意图"生成的唯一值，网络重试、重复点击都带同一个
 * @param items 购买的商品和数量
 */
public record PlaceOrderCommand(long userId, String idempotencyKey, List<Item> items) {

    public PlaceOrderCommand {
        items = List.copyOf(items);
    }

    /** 同一个商品出现多次时合并数量，并按商品 id 排序 */
    public Map<Long, Integer> mergedQuantities() {
        return items.stream().collect(Collectors.toMap(Item::skuId, Item::quantity, Integer::sum, TreeMap::new));
    }

    /**
     * 请求内容的指纹。同一个幂等键再次出现时，用它判断是"同一个请求的重试"还是"拿旧 key 发了一个不同的请求"：
     * 后者是客户端的 bug，不能悄悄返回旧订单，要明确报错。
     */
    public String requestHash() {
        String canonical = mergedQuantities().entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    /**
     * 一个购买项。
     *
     * @param skuId 商品 id
     * @param quantity 数量
     */
    public record Item(long skuId, int quantity) {}
}
