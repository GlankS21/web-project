package com.shop.product.service.impl;

import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ConflictException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.product.dto.request.CategoryRequest;
import com.shop.product.dto.response.CategoryResponse;
import com.shop.product.entity.Category;
import com.shop.product.mapper.CategoryMapper;
import com.shop.product.repository.CategoryRepository;
import com.shop.product.repository.ProductRepository;
import com.shop.product.service.CategoryService;
import com.shop.product.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getTree() {
        List<Category> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        Map<Long, List<CategoryResponse>> childrenByParent = new LinkedHashMap<>();
        categories.stream()
                .filter(category -> !category.isRoot())
                .forEach(child -> childrenByParent
                        .computeIfAbsent(child.getParent().getId(), id -> new ArrayList<>())
                        .add(categoryMapper.toResponse(child)));
        return categories.stream()
                .filter(Category::isRoot)
                .map(root -> categoryMapper.toResponse(root, childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategory(Long categoryId) {
        return toDetailResponse(findCategory(categoryId));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        return toDetailResponse(categoryRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("category.error.not-found")));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String slug = request.slug() != null ? request.slug() : deriveSlug(request.name());
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("category.error.slug-taken", slug);
        }

        Category category = new Category();
        categoryMapper.updateFromRequest(request, category);
        category.setSlug(slug);
        category.setParent(resolveParent(request.parentId(), null));
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = findCategory(categoryId);
        // Keep existing URLs stable: the slug changes only when explicitly requested
        if (request.slug() != null && !request.slug().equals(category.getSlug())) {
            if (categoryRepository.existsBySlugAndIdNot(request.slug(), categoryId)) {
                throw new ConflictException("category.error.slug-taken", request.slug());
            }
            category.setSlug(request.slug());
        }
        categoryMapper.updateFromRequest(request, category);
        category.setParent(resolveParent(request.parentId(), category));
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        return toDetailResponse(categoryRepository.saveAndFlush(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = findCategory(categoryId);
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new BusinessException("category.error.has-children");
        }
        if (productRepository.existsByCategoryId(categoryId)) {
            throw new BusinessException("category.error.has-products");
        }
        categoryRepository.delete(category);
    }

    /**
     * Enforces the two-level tree: the parent must be a root category, and a category that has
     * children cannot itself become a child.
     */
    private Category resolveParent(Long parentId, Category category) {
        if (parentId == null) {
            return null;
        }
        if (category != null && Objects.equals(parentId, category.getId())) {
            throw new BadRequestException("category.error.own-parent");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new BadRequestException("category.error.parent-not-found", String.valueOf(parentId)));
        if (!parent.isRoot()) {
            throw new BusinessException("category.error.too-deep");
        }
        if (category != null && categoryRepository.existsByParentId(category.getId())) {
            throw new BusinessException("category.error.parent-has-children");
        }
        return parent;
    }

    private String deriveSlug(String name) {
        String slug = SlugUtils.slugify(name, SLUG_MAX_LENGTH);
        if (slug.isEmpty()) {
            throw new BadRequestException("category.error.slug-not-derivable");
        }
        return slug;
    }

    private CategoryResponse toDetailResponse(Category category) {
        List<CategoryResponse> children = category.isRoot()
                ? categoryRepository.findAllByParentIdOrderBySortOrderAscNameAsc(category.getId()).stream()
                .map(categoryMapper::toResponse)
                .toList()
                : List.of();
        return categoryMapper.toResponse(category, children);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("category.error.not-found"));
    }
}
