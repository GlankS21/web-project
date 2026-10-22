package com.shop.product.controller;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import com.shop.product.config.OpenApiConfig;
import com.shop.product.dto.request.ProductRequest;
import com.shop.product.dto.request.ProductSearchCriteria;
import com.shop.product.dto.request.ProductStatusRequest;
import com.shop.product.dto.response.ProductResponse;
import com.shop.product.dto.response.ProductSummaryResponse;
import com.shop.product.entity.ProductStatus;
import com.shop.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Товары", description = "Просмотр каталога (доступен всем) и управление товарами (ROLE_ADMIN). "
        + "Покупатели видят только товары в статусе ACTIVE, администраторы — также DRAFT и ARCHIVED.")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final MessageSource messageSource;

    @Operation(summary = "Поиск по каталогу",
            description = "Сортировка по полям price, name, createdAt, updatedAt, sku, ratingAverage. "
                    + "Пример: sort=price,asc")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> search(
            @Parameter(description = "Часть названия, артикула или бренда")
            @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "Идентификатор категории; корневая категория включает свои подкатегории")
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @Parameter(description = "Только для администраторов; для остальных пользователей не учитывается")
            @RequestParam(name = "status", required = false) ProductStatus status,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, categoryId, brand, minPrice, maxPrice, status);
        return ApiResponse.success(productService.searchProducts(criteria, pageable, SecurityUtils.currentUserIsAdmin()));
    }

    @Operation(summary = "Несколько товаров по идентификаторам (не более 100)",
            description = "Товары возвращаются в порядке запроса; неизвестные и недоступные "
                    + "идентификаторы пропускаются.")
    @GetMapping("/by-ids")
    public ApiResponse<List<ProductSummaryResponse>> getByIds(@RequestParam(name = "ids") List<Long> ids) {
        return ApiResponse.success(productService.getProductsByIds(ids, SecurityUtils.currentUserIsAdmin()));
    }

    @Operation(summary = "Товар по идентификатору")
    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> get(@PathVariable("productId") Long productId) {
        return ApiResponse.success(productService.getProduct(productId, SecurityUtils.currentUserIsAdmin()));
    }

    @Operation(summary = "Товар по адресу (slug)")
    @GetMapping("/slug/{slug}")
    public ApiResponse<ProductResponse> getBySlug(@PathVariable("slug") String slug) {
        return ApiResponse.success(productService.getProductBySlug(slug, SecurityUtils.currentUserIsAdmin()));
    }

    @Operation(summary = "Создать товар (в статусе DRAFT, если статус не указан)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(message("product.created"), productService.createProduct(request));
    }

    @Operation(summary = "Изменить товар (артикул не меняется; если изображения не переданы, они сохраняются)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{productId}")
    public ApiResponse<ProductResponse> update(@PathVariable("productId") Long productId,
                                               @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(message("product.updated"), productService.updateProduct(productId, request));
    }

    @Operation(summary = "Опубликовать, снять с публикации или перенести товар в архив",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{productId}/status")
    public ApiResponse<ProductResponse> changeStatus(@PathVariable("productId") Long productId,
                                                     @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.success(message("product.status-updated"), productService.changeStatus(productId, request.status()));
    }

    @Operation(summary = "Перенести товар в архив (мягкое удаление: прошлые заказы по-прежнему ссылаются на него)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> archive(@PathVariable("productId") Long productId) {
        productService.changeStatus(productId, ProductStatus.ARCHIVED);
        return ApiResponse.message(message("product.archived"));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}
