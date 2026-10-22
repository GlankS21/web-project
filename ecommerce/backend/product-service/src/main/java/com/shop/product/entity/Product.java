package com.shop.product.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sellable product. The SKU is immutable once created because stock and orders refer to it.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true, updatable = false)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 220, unique = true)
    private String slug;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String brand;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    /** Previous ("was") price shown struck through; always greater than {@link #price}. */
    @Column(name = "compare_at_price", precision = 14, scale = 2)
    private BigDecimal compareAtPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /**
     * Mean review rating rounded to one decimal, 0 without reviews. Maintained by the review service
     * with a bulk update (see {@code ProductRepository#updateRating}), never through this entity.
     */
    @Column(name = "rating_average", nullable = false, precision = 2, scale = 1, insertable = false, updatable = false)
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
    private int ratingCount;

    /** Batch-fetched so that a page of products loads its thumbnails in one extra query. */
    @BatchSize(size = 100)
    @OrderBy("sortOrder ASC, id ASC")
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @Version
    private Long version;

    public void replaceImages(List<ProductImage> newImages) {
        images.clear();
        newImages.forEach(image -> {
            image.setProduct(this);
            images.add(image);
        });
    }

    public String thumbnailUrl() {
        return images.isEmpty() ? null : images.getFirst().getUrl();
    }
}
