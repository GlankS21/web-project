package com.shop.product.service;

import com.shop.product.dto.request.CategoryRequest;
import com.shop.product.dto.response.CategoryResponse;

import java.util.List;

/**
 * Two-level category tree: root categories and their direct children.
 */
public interface CategoryService {

    int SLUG_MAX_LENGTH = 120;

    /**
     * Root categories ordered by sort order then name, each with its children.
     */
    List<CategoryResponse> getTree();

    CategoryResponse getCategory(Long categoryId);

    CategoryResponse getCategoryBySlug(String slug);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Long categoryId, CategoryRequest request);

    /**
     * Refused while the category has sub-categories or products (archived ones included, since
     * they stay attached to past orders).
     */
    void deleteCategory(Long categoryId);
}
