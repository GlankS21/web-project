package com.shop.product.service.impl;

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
import com.shop.product.entity.ProductStatus;
import com.shop.product.mapper.ProductMapperImpl;
import com.shop.product.repository.CategoryRepository;
import com.shop.product.repository.ProductRepository;
import com.shop.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductServiceImpl service;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(productRepository, categoryRepository, new ProductMapperImpl(),
                new CatalogProperties("VND"), eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        category = new Category();
        category.setId(2L);
        category.setName("Điện thoại");
        category.setSlug("dien-thoai");
    }

    @Test
    void createProduct_derivesSlugDefaultsToDraftAndPublishesEvent() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product product = inv.getArgument(0);
            product.setId(42L);
            return product;
        });

        ProductResponse response = service.createProduct(request("PHN-NEW-1", "Điện thoại Mới", null,
                new BigDecimal("1000"), null, List.of(new ProductImageRequest("https://img/1.jpg", "front"),
                        new ProductImageRequest("https://img/2.jpg", null))));

        assertThat(response.slug()).isEqualTo("dien-thoai-moi");
        assertThat(response.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.category().slug()).isEqualTo("dien-thoai");
        assertThat(response.images()).extracting(image -> image.sortOrder()).containsExactly(0, 1);

        ArgumentCaptor<ProductCreatedEvent> event = ArgumentCaptor.forClass(ProductCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().productId()).isEqualTo(42L);
        assertThat(event.getValue().sku()).isEqualTo("PHN-NEW-1");
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void createProduct_appendsSkuWhenDerivedSlugIsTaken() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.existsBySlug("iphone-15")).thenReturn(true);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = service.createProduct(request("PHN-IP15-B", "iPhone 15", null,
                new BigDecimal("1000"), null, null));

        assertThat(response.slug()).isEqualTo("iphone-15-phn-ip15-b");
    }

    @Test
    void createProduct_rejectsDuplicateSku() {
        when(productRepository.existsBySku("PHN-IP15-128")).thenReturn(true);

        assertThatThrownBy(() -> service.createProduct(request("PHN-IP15-128", "Copy", null,
                new BigDecimal("1000"), null, null)))
                .isInstanceOf(ConflictException.class);
        verify(productRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createProduct_rejectsCompareAtPriceNotAbovePrice() {
        assertThatThrownBy(() -> service.createProduct(request("PHN-X-1", "X", null,
                new BigDecimal("1000"), new BigDecimal("1000"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("compareAtPrice");
    }

    @Test
    void createProduct_rejectsExplicitSlugInUse() {
        when(productRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.createProduct(request("PHN-X-1", "X", "taken",
                new BigDecimal("1000"), null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateProduct_refusesToChangeSku() {
        when(productRepository.findWithDetailsById(1L)).thenReturn(Optional.of(product(1L, ProductStatus.ACTIVE)));

        assertThatThrownBy(() -> service.updateProduct(1L, request("OTHER-SKU", "Name", null,
                new BigDecimal("1000"), null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SKU");
    }

    @Test
    void updateProduct_keepsSlugStatusAndImagesWhenOmitted() {
        Product existing = product(1L, ProductStatus.ACTIVE);
        existing.setSlug("old-slug");
        when(productRepository.findWithDetailsById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        ProductResponse response = service.updateProduct(1L, new ProductRequest("SKU-1", "Renamed", null, null, null,
                null, new BigDecimal("2000"), null, 2L, null, null));

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.slug()).isEqualTo("old-slug");
        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.price()).isEqualByComparingTo("2000");
    }

    @Test
    void getProduct_hiddenProductIsNotFoundForCustomersButVisibleToAdmins() {
        when(productRepository.findWithDetailsById(7L)).thenReturn(Optional.of(product(7L, ProductStatus.DRAFT)));

        assertThatThrownBy(() -> service.getProduct(7L, false)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.getProduct(7L, true).status()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void getProductsByIds_keepsRequestedOrderAndSkipsUnknownIds() {
        when(productRepository.findAllByIdInAndStatus(List.of(3L, 1L, 99L), ProductStatus.ACTIVE))
                .thenReturn(List.of(product(1L, ProductStatus.ACTIVE), product(3L, ProductStatus.ACTIVE)));

        List<ProductSummaryResponse> result = service.getProductsByIds(List.of(3L, 1L, 99L, 3L), false);

        assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(3L, 1L);
    }

    @Test
    void getProductsByIds_rejectsTooManyIds() {
        List<Long> ids = LongStream.rangeClosed(1, ProductService.MAX_BATCH_SIZE + 1).boxed().toList();

        assertThatThrownBy(() -> service.getProductsByIds(ids, false)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void searchProducts_rejectsUnknownSortProperty() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null, null, null, null);

        assertThatThrownBy(() -> service.searchProducts(criteria,
                PageRequest.of(0, 20, Sort.by("description")), false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void searchProducts_rejectsInvertedPriceRange() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null,
                new BigDecimal("500"), new BigDecimal("100"), null);

        assertThatThrownBy(() -> service.searchProducts(criteria, PageRequest.of(0, 20), false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void searchProducts_unknownCategoryReturnsEmptyPage() {
        when(categoryRepository.findIdsOfSelfAndChildren(404L)).thenReturn(List.of());
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, 404L, null, null, null, null);

        assertThat(service.searchProducts(criteria, PageRequest.of(0, 20), false).getTotalElements()).isZero();
        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    private ProductRequest request(String sku, String name, String slug, BigDecimal price, BigDecimal compareAt,
                                   List<ProductImageRequest> images) {
        return new ProductRequest(sku, name, slug, "short", "long", "Brand", price, compareAt, 2L, null, images);
    }

    private Product product(Long id, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU-" + id);
        product.setName("Product " + id);
        product.setSlug("product-" + id);
        product.setPrice(new BigDecimal("1000"));
        product.setCategory(category);
        product.setStatus(status);
        return product;
    }
}
