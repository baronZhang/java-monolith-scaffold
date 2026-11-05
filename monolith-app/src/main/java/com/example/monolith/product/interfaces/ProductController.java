package com.example.monolith.product.interfaces;

import com.example.monolith.product.application.ProductCommandService;
import com.example.monolith.product.application.ProductQueryService;
import com.example.monolith.product.application.ProductView;
import com.example.monolith.shared.api.Result;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品接口：浏览登录即可，改价需要 product:edit（演示账号里只有 admin 的 '*' 满足）。 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductQueryService queries;
    private final ProductCommandService commands;

    public ProductController(ProductQueryService queries, ProductCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    public Result<List<ProductView>> list() {
        return Result.ok(queries.listAll());
    }

    @GetMapping("/{id}")
    public Result<ProductView> get(@PathVariable long id) {
        return Result.ok(queries.find(id).orElseThrow(() -> BusinessException.notFound("商品 " + id + " 不存在")));
    }

    @PutMapping("/{id}/price")
    @RequiresPermission("product:edit")
    public Result<ProductView> changePrice(@PathVariable long id, @Valid @RequestBody PriceRequest request) {
        return Result.ok(commands.changePrice(id, request.price()));
    }

    public record PriceRequest(
            @NotNull(message = "价格不能为空")
            @DecimalMin(value = "0.01", message = "价格必须大于 0")
            @Digits(integer = 10, fraction = 2, message = "价格最多两位小数")
            BigDecimal price) {}
}
