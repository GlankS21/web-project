package com.shop.order.service.impl;

import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.exception.ServiceUnavailableException;
import com.shop.common.security.AuthenticatedUser;
import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductSummary;
import com.shop.order.config.OrderProperties;
import com.shop.order.dto.request.CreateOrderRequest;
import com.shop.order.dto.request.OrderItemRequest;
import com.shop.order.dto.request.UpdateOrderStatusRequest;
import com.shop.order.dto.response.OrderResponse;
import com.shop.order.dto.response.OrderSummaryResponse;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderItem;
import com.shop.order.entity.OrderStatus;
import com.shop.order.mapper.OrderMapper;
import com.shop.order.repository.OrderRepository;
import com.shop.order.repository.OrderSpecifications;
import com.shop.order.service.OrderLifecycle;
import com.shop.order.service.OrderService;
import com.shop.order.util.OrderNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");
    private static final Set<OrderStatus> ADMIN_TARGET_STATUSES = Set.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderLifecycle orderLifecycle;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderMapper orderMapper;
    private final OrderProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Not transactional as a whole: no database connection is held during the call to product-service.
     */
    @Override
    public OrderPlacement placeOrder(AuthenticatedUser customer, CreateOrderRequest request, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            Optional<OrderResponse> previous = transactionTemplate.execute(status -> orderRepository
                    .findByUserIdAndIdempotencyKey(customer.id(), key)
                    .map(orderMapper::toResponse));
            if (previous.isPresent()) {
                log.info("Checkout retried with idempotency key {}, returning order {}", key,
                        previous.get().orderNumber());
                return new OrderPlacement(previous.get(), false);
            }
        }

        Map<Long, Integer> quantities = mergeQuantities(request.items());
        Map<Long, ProductSummary> products = fetchProducts(quantities.keySet());

        Order order = new Order();
        order.setOrderNumber(orderNumberGenerator.next());
        order.setUserId(customer.id());
        order.setUserEmail(customer.email());
        order.setPaymentMethod(request.paymentMethod());
        order.setShippingAddress(orderMapper.toEntity(request.shippingAddress()));
        order.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        order.setIdempotencyKey(key);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            ProductSummary product = products.get(line.getKey());
            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setSku(product.sku());
            item.setProductName(product.name());
            item.setThumbnailUrl(product.thumbnailUrl());
            item.setUnitPrice(product.price());
            item.setQuantity(line.getValue());
            item.setLineTotal(product.price().multiply(BigDecimal.valueOf(line.getValue())));
            order.addItem(item);
            subtotal = subtotal.add(item.getLineTotal());
        }
        BigDecimal shippingFee = properties.shipping().feeFor(subtotal);
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(subtotal.add(shippingFee));
        order.setCurrency(singleCurrency(products.values()));

        OrderResponse placed = transactionTemplate.execute(status -> orderMapper.toResponse(orderLifecycle.place(order)));
        log.info("Order {} placed by {}: {} product(s), total {} {}", placed.orderNumber(), customer.id(),
                quantities.size(), placed.totalAmount(), placed.currency());
        return new OrderPlacement(placed, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrdersOf(UUID userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findAllByUserId(userId, pageable)
                : orderRepository.findAllByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(page, orderMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, AuthenticatedUser caller) {
        Order order = orderRepository.findById(orderId)
                .filter(found -> caller.isAdmin() || found.isOwnedBy(caller.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, AuthenticatedUser caller, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(found -> caller.isAdmin() || found.isOwnedBy(caller.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        if (!order.getStatus().isCancellable()) {
            throw new BusinessException("Order %s is %s and can no longer be cancelled"
                    .formatted(order.getOrderNumber(), order.getStatus()));
        }
        String who = order.isOwnedBy(caller.id()) ? "customer" : "administrator";
        String fullReason = StringUtils.hasText(reason)
                ? "Cancelled by %s: %s".formatted(who, reason.trim())
                : "Cancelled by " + who;
        orderLifecycle.cancel(order, fullReason, caller.id());
        return orderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> searchOrders(OrderStatus status, UUID userId, String keyword,
                                                           Pageable pageable) {
        Specification<Order> specification = Specification.allOf(
                OrderSpecifications.hasStatus(status),
                OrderSpecifications.belongsTo(userId),
                OrderSpecifications.matchesKeyword(keyword));
        return PageResponse.from(orderRepository.findAll(specification, pageable), orderMapper::toSummary);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request, UUID adminId) {
        if (!ADMIN_TARGET_STATUSES.contains(request.status())) {
            throw new BadRequestException("Status can only be set to SHIPPED or DELIVERED; "
                    + "use the cancel endpoint to cancel an order");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        order.changeStatus(request.status(), request.note(), adminId, clock.instant());
        log.info("Order {} marked {} by {}", order.getOrderNumber(), request.status(), adminId);
        return orderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    /**
     * Sums lines of the same product, keeping the order in which products were first listed.
     */
    private Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        items.forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        if (quantities.size() > properties.maxItems()) {
            throw new BadRequestException("An order can contain at most %d different products"
                    .formatted(properties.maxItems()));
        }
        quantities.forEach((productId, quantity) -> {
            if (quantity > properties.maxQuantityPerItem()) {
                throw new BadRequestException("At most %d units of product %d can be ordered"
                        .formatted(properties.maxQuantityPerItem(), productId));
            }
        });
        return quantities;
    }

    private Map<Long, ProductSummary> fetchProducts(Set<Long> productIds) {
        if (productIds.size() > ProductClient.MAX_BATCH_SIZE) {
            throw new BadRequestException("Too many products in one order");
        }
        ApiResponse<List<ProductSummary>> response = productClient.getProductsByIds(List.copyOf(productIds));
        if (response == null || response.getData() == null) {
            throw new ServiceUnavailableException("The product catalogue returned no data, please retry");
        }
        Map<Long, ProductSummary> products = response.getData().stream()
                .filter(product -> product.price() != null && product.price().signum() > 0)
                .collect(Collectors.toMap(ProductSummary::id, Function.identity(), (first, second) -> first));
        List<Long> unavailable = productIds.stream().filter(id -> !products.containsKey(id)).toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessException("These products are not available for sale: " + unavailable);
        }
        return products;
    }

    private String singleCurrency(Iterable<ProductSummary> products) {
        Set<String> currencies = new HashSet<>();
        products.forEach(product -> currencies.add(product.currency()));
        if (currencies.size() != 1 || currencies.contains(null)) {
            throw new BusinessException("Products priced in different currencies cannot be ordered together");
        }
        return currencies.iterator().next();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new BadRequestException("Idempotency-Key must be 8 to 100 letters, digits, '-' or '_'");
        }
        return key;
    }
}
