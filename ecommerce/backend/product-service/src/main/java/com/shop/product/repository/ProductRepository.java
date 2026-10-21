package com.shop.product.repository;

import com.shop.product.entity.Product;
import com.shop.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
