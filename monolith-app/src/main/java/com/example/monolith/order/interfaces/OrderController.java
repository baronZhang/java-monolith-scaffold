package com.example.monolith.order.interfaces;

import com.example.monolith.order.application.OrderCancellationService;
import com.example.monolith.order.application.OrderPaymentService;
import com.example.monolith.order.application.OrderQueryService;
import com.example.monolith.order.application.OrderView;
import com.example.monolith.order.application.PlaceOrderCommand;
import com.example.monolith.order.application.PlaceOrderService;
import com.example.monolith.shared.api.PageResult;
import com.example.monolith.shared.api.Result;
import com.example.monolith.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口：登录即可下单；只能看、付、取消自己的订单（order:manage 可以看全部）。
 * 和微服务版约定的是同一组路径，方便对比。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** 幂等重放时加的响应头（和 Stripe 的做法一样），客户端据此知道这次返回的是已有订单 */
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PlaceOrderService placeOrderService;
    private final OrderPaymentService paymentService;
    private final OrderCancellationService cancellationService;
    private final OrderQueryService queryService;

    public OrderController(
            PlaceOrderService placeOrderService,
            OrderPaymentService paymentService,
            OrderCancellationService cancellationService,
            OrderQueryService queryService) {
        this.placeOrderService = placeOrderService;
        this.paymentService = paymentService;
        this.cancellationService = cancellationService;
        this.queryService = queryService;
    }

    /**
     * 下单。请求头 Idempotency-Key 必填：客户端为"这一次下单"生成一个唯一值（比如 UUID），重试时带同一个。
     * 参数上的约束注解由 Spring 6.1 起内置的方法参数校验处理，不用在类上加 @Validated。
     */
    @PostMapping
    public ResponseEntity<Result<OrderView>> place(
            @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "Idempotency-Key 必须是 8~64 位字母、数字、横线或下划线")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        List<PlaceOrderCommand.Item> items = request.items().stream()
                .map(item -> new PlaceOrderCommand.Item(item.skuId(), item.quantity()))
                .toList();
        PlaceOrderService.PlaceOrderResult result =
                placeOrderService.place(new PlaceOrderCommand(CurrentUser.id(), idempotencyKey, items));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(Result.ok(result.order()));
    }

    @GetMapping
    public Result<PageResult<OrderView>> listMine(
            @RequestParam(required = false) Long page, @RequestParam(required = false) Long pageSize) {
        return Result.ok(queryService.listMine(CurrentUser.id(), page, pageSize));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderView> get(@PathVariable long orderNo) {
        return Result.ok(queryService.get(orderNo, CurrentUser.id(), CurrentUser.hasPermission("order:manage")));
    }

    /** 模拟支付 */
    @PostMapping("/{orderNo}/pay")
    public Result<OrderView> pay(@PathVariable long orderNo) {
        return Result.ok(paymentService.pay(orderNo, CurrentUser.id()));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<OrderView> cancel(@PathVariable long orderNo) {
        return Result.ok(cancellationService.cancelByUser(orderNo, CurrentUser.id()));
    }

    /** 下单请求体：{"items": [{"skuId": 6, "quantity": 1}]} */
    public record PlaceOrderRequest(
            @NotEmpty(message = "订单至少要有一个商品") @Size(max = 20, message = "一个订单最多 20 种商品")
            List<@Valid @NotNull(message = "商品项不能为空") Item> items) {

        public record Item(
                @NotNull(message = "skuId 不能为空") Long skuId,

                @NotNull(message = "购买数量不能为空")
                @Min(value = 1, message = "购买数量至少为 1")
                @Max(value = 99, message = "单个商品最多购买 99 件")
                Integer quantity) {}
    }
}
