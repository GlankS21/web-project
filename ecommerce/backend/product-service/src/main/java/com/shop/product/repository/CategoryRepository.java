package com.shop.product.repository;

import com.shop.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    List<Category> findAllByParentIdOrderBySortOrderAscNameAsc(Long parentId);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByParentId(Long parentId);

    /**
     * The category itself plus its direct children (the tree has two levels).
     */
    @Query("select c.id from Category c where c.id = :id or c.parent.id = :id")
    List<Long> findIdsOfSelfAndChildren(@Param("id") Long id);
}
