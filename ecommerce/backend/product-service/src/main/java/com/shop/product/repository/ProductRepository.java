package com.shop.product.repository;

import com.shop.product.entity.Product;
import com.shop.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Category is fetched with the page; images are batch-loaded (see {@code Product#images}), which
     * avoids paginating a collection fetch in memory.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findWithDetailsBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdInAndStatus(Collection<Long> ids, ProductStatus status);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Locks the product row until the end of the transaction; review writes of one product are
     * serialised by it, so that each recomputed rating includes every committed review.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdateById(@Param("id") Long id);

    @Query("select p.id from Product p where p.id in :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);

    /**
     * Sets the rating summary without touching the version or the modification time: a review is
     * not an edit of the product and must not make a concurrent admin update fail.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "update products set rating_average = :average, rating_count = :count where id = :id",
            nativeQuery = true)
    int updateRating(@Param("id") Long id, @Param("average") BigDecimal average, @Param("count") int count);
}
