package com.example.monolith.inventory.interfaces;

import com.example.monolith.inventory.application.InventoryService;
import com.example.monolith.inventory.domain.Stock;
import com.example.monolith.shared.api.Result;
import com.example.monolith.shared.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 库存接口：查询登录即可，调整需要 inventory:edit（演示账号里只有 admin 的 '*' 满足）。 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{skuId}")
    public Result<Stock> get(@PathVariable long skuId) {
        return Result.ok(inventoryService.getStock(skuId));
    }

    @PutMapping("/{skuId}")
    @RequiresPermission("inventory:edit")
    public Result<Stock> setAvailable(@PathVariable long skuId, @Valid @RequestBody AdjustRequest request) {
        return Result.ok(inventoryService.setAvailable(skuId, request.available()));
    }

    /** 调整请求体：{"available": 100} */
    public record AdjustRequest(
            @NotNull(message = "available 不能为空")
            @Min(value = 0, message = "可售库存不能为负数")
            @Max(value = 1_000_000, message = "可售库存不能超过 1000000")
            Integer available) {}
}
