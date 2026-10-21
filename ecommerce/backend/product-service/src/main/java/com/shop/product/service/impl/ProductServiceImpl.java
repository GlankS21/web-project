package com.shop.product.service.impl;

import com.shop.common.dto.PageResponse;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.ConflictException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.product.config.CatalogProperties;
import com.shop.product.dto.request.ProductImageRequest;
import com.shop.product.dto.request.ProductRequest;
import com.shop.product.dto.request.ProductSearchCriteria;
import com.shop.product.dto.response.ProductResponse;
import com.shop.product.dto.response.ProductSummaryResponse;
import com.shop.product.entity.Category;
import com.shop.product.entity.Product;
import com.shop.product.entity.ProductImage;
import com.shop.product.entity.ProductStatus;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.repository.CategoryRepository;
import com.shop.product.repository.ProductRepository;
import com.shop.product.repository.ProductSpecifications;
import com.shop.product.service.ProductService;
import com.shop.product.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final CatalogProperties catalogProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchProducts(ProductSearchCriteria criteria, Pageable pageable,
                                                               boolean includeHidden) {
        validateSort(pageable.getSort());
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }

        List<Long> categoryIds = null;
        if (criteria.categoryId() != null) {
            categoryIds = categoryRepository.findIdsOfSelfAndChildren(criteria.categoryId());
            if (categoryIds.isEmpty()) {
                return PageResponse.from(Page.empty(pageable));
            }
        }
        ProductStatus status = includeHidden ? criteria.status() : ProductStatus.ACTIVE;

        Specification<Product> specification = Specification.allOf(
                ProductSpecifications.hasStatus(status),
                ProductSpecifications.matchesKeyword(criteria.keyword()),
                ProductSpecifications.inCategories(categoryIds),
                ProductSpecifications.hasBrand(criteria.brand()),
                ProductSpecifications.priceAtLeast(criteria.minPrice()),
                ProductSpecifications.priceAtMost(criteria.maxPrice()));
        return PageResponse.from(productRepository.findAll(specification, pageable), this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId, boolean includeHidden) {
        return visible(productRepository.findWithDetailsById(productId), includeHidden)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug, boolean includeHidden) {
        return visible(productRepository.findWithDetailsBySlug(slug), includeHidden)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getProductsByIds(Collection<Long> productIds, boolean includeHidden) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BadRequestException("At most %d product ids can be requested at once".formatted(MAX_BATCH_SIZE));
        }
        List<Product> products = includeHidden
                ? productRepository.findAllByIdIn(ids)
                : productRepository.findAllByIdInAndStatus(ids, ProductStatus.ACTIVE);
        Map<Long, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("A product with SKU '%s' already exists".formatted(request.sku()));
        }
        validatePrices(request.price(), request.compareAtPrice());

        Product product = new Product();
        productMapper.updateFromRequest(request, product);
        product.setSku(request.sku());
        product.setSlug(request.slug() != null ? requireFreeSlug(request.slug(), null) : deriveSlug(request));
        product.setCategory(findCategory(request.categoryId()));
        product.setStatus(request.status() == null ? ProductStatus.DRAFT : request.status());
        product.replaceImages(toImages(request.images()));

        Product saved = productRepository.saveAndFlush(product);
        eventPublisher.publishEvent(new ProductCreatedEvent(
                UUID.randomUUID(), clock.instant(), saved.getId(), saved.getSku(), saved.getName()));
        log.info("Created product {} (SKU {})", saved.getId(), saved.getSku());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = findProduct(productId);
        if (!product.getSku().equals(request.sku())) {
            throw new BadRequestException("The SKU of a product cannot be changed");
        }
        validatePrices(request.price(), request.compareAtPrice());

        productMapper.updateFromRequest(request, product);
        // Keep existing URLs stable: the slug changes only when explicitly requested
        if (request.slug() != null && !request.slug().equals(product.getSlug())) {
            product.setSlug(requireFreeSlug(request.slug(), productId));
        }
        if (!Objects.equals(product.getCategory().getId(), request.categoryId())) {
            product.setCategory(findCategory(request.categoryId()));
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        if (request.images() != null) {
            product.replaceImages(toImages(request.images()));
        }
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse changeStatus(Long productId, ProductStatus status) {
        Product product = findProduct(productId);
        if (product.getStatus() != status) {
            log.info("Product {} status {} -> {}", productId, product.getStatus(), status);
            product.setStatus(status);
        }
        return toResponse(productRepository.saveAndFlush(product));
    }

    private Optional<Product> visible(Optional<Product> product, boolean includeHidden) {
        return product.filter(p -> includeHidden || p.getStatus() == ProductStatus.ACTIVE);
    }

    private void validateSort(Sort sort) {
        sort.forEach(order -> {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException("Products can be sorted by %s only"
                        .formatted(String.join(", ", SORTABLE_PROPERTIES.stream().sorted().toList())));
            }
        });
    }

    private void validatePrices(BigDecimal price, BigDecimal compareAtPrice) {
        if (compareAtPrice != null && compareAtPrice.compareTo(price) <= 0) {
            throw new BadRequestException("compareAtPrice must be greater than price");
        }
    }

    /**
     * Slug from the name; when another product already uses it, the SKU is appended.
     */
    private String deriveSlug(ProductRequest request) {
        String base = SlugUtils.slugify(request.name(), 150);
        String skuPart = SlugUtils.slugify(request.sku(), 64);
        String candidate = base.isEmpty() ? skuPart : base;
        if (productRepository.existsBySlug(candidate)) {
            candidate = base.isEmpty() ? skuPart : base + "-" + skuPart;
        }
        return requireFreeSlug(candidate, null);
    }

    private String requireFreeSlug(String slug, Long productId) {
        boolean taken = productId == null
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, productId);
        if (taken) {
            throw new ConflictException("A product with slug '%s' already exists".formatted(slug));
        }
        return slug;
    }

    private List<ProductImage> toImages(List<ProductImageRequest> requests) {
        List<ProductImage> images = new ArrayList<>();
        if (requests != null) {
            for (int i = 0; i < requests.size(); i++) {
                ProductImage image = productMapper.toImageEntity(requests.get(i));
                image.setSortOrder(i);
                images.add(image);
            }
        }
        return images;
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category %d does not exist".formatted(categoryId)));
    }

    private Product findProduct(Long productId) {
        return productRepository.findWithDetailsById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }

    private ProductResponse toResponse(Product product) {
        return productMapper.toResponse(product, catalogProperties.currency());
    }

    private ProductSummaryResponse toSummary(Product product) {
        return productMapper.toSummary(product, catalogProperties.currency());
    }
}
