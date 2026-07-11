package store.mailstock.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.order.dto.ManualDeliverRequest;
import store.mailstock.order.dto.OrderCreateRequest;
import store.mailstock.order.entity.Order;
import store.mailstock.order.entity.OrderItem;
import store.mailstock.order.service.OrderService;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orders;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<Order> create(@Valid @RequestBody OrderCreateRequest req) {
        return ApiResponse.ok(orders.createOrder(SecurityUtils.currentUserId(), req));
    }

    /** Cart checkout preview: prices the items + validates a coupon without charging or consuming it. */
    @PostMapping("/me/quote")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<store.mailstock.order.dto.OrderQuote> quote(@Valid @RequestBody OrderCreateRequest req) {
        return ApiResponse.ok(orders.quote(SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<PageResponse<Order>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(orders.mine(SecurityUtils.currentUserId(), PageRequest.of(page, size))));
    }

    /** The buyer's purchased-email vault: every delivered item with full credentials. */
    @GetMapping("/me/emails")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<List<OrderItem>> myEmails() {
        return ApiResponse.ok(orders.myEmails(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMIN')")
    public ApiResponse<Order> get(@PathVariable Long id) {
        Order o = orders.get(id);
        if (!SecurityUtils.currentUser().hasRole(store.mailstock.auth.entity.Role.ADMIN)
                && !o.getBuyerId().equals(SecurityUtils.currentUserId()))
            throw store.mailstock.common.exception.ApiException.forbidden("Not your order");
        return ApiResponse.ok(o);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<Order>> adminList(
            @RequestParam(required = false) Order.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(orders.adminList(status, q, PageRequest.of(page, size))));
    }

    /** Admin manual delivery (warranty/replacement). chargeBalance=false delivers for free. */
    @PostMapping("/admin/manual-deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Order> manualDeliver(@Valid @RequestBody ManualDeliverRequest req) {
        return ApiResponse.ok(orders.manualDeliver(SecurityUtils.currentUserId(), req));
    }
}
