package com.shop.product.service;

import com.shop.common.dto.PageResponse;
import com.shop.product.dto.request.ProductRequest;
import com.shop.product.dto.request.ProductSearchCriteria;
import com.shop.product.entity.ProductStatus;
import com.shop.product.dto.response.ProductResponse;
import com.shop.product.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Read operations take {@code includeHidden}: {@code true} for administrators, who also see DRAFT and
 * ARCHIVED products. For everybody else a hidden product behaves as not found.
 */
public interface ProductService {

    int SLUG_MAX_LENGTH = 220;
    int MAX_BATCH_SIZE = 100;
    Set<String> SORTABLE_PROPERTIES = Set.of("price", "name", "createdAt", "updatedAt", "sku", "ratingAverage");

    PageResponse<ProductSummaryResponse> searchProducts(ProductSearchCriteria criteria, Pageable pageable,
                                                        boolean includeHidden);

    ProductResponse getProduct(Long productId, boolean includeHidden);

    ProductResponse getProductBySlug(String slug, boolean includeHidden);

    /**
     * Products in the order of the requested ids; unknown (or hidden) ids are left out.
     * Used by cart and order services to read current names and prices.
     */
    List<ProductSummaryResponse> getProductsByIds(Collection<Long> productIds, boolean includeHidden);

    /**
     * Publishes {@code product.created} after commit so that inventory-service opens a stock record.
     */
    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long productId, ProductRequest request);

    ProductResponse changeStatus(Long productId, ProductStatus status);
}
