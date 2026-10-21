package com.shop.product.controller;

import com.shop.common.dto.ApiResponse;
import com.shop.product.config.OpenApiConfig;
import com.shop.product.dto.request.CategoryRequest;
import com.shop.product.dto.response.CategoryResponse;
import com.shop.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Categories", description = "Two-level category tree. Reads are public, writes require ROLE_ADMIN.")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Category tree (root categories with their children)")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> tree() {
        return ApiResponse.success(categoryService.getTree());
    }

    @Operation(summary = "Get a category with its children")
    @GetMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> get(@PathVariable("categoryId") Long categoryId) {
        return ApiResponse.success(categoryService.getCategory(categoryId));
    }

    @Operation(summary = "Get a category by slug")
    @GetMapping("/slug/{slug}")
    public ApiResponse<CategoryResponse> getBySlug(@PathVariable("slug") String slug) {
        return ApiResponse.success(categoryService.getCategoryBySlug(slug));
    }

    @Operation(summary = "Create a category", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success("Category created", categoryService.createCategory(request));
    }

    @Operation(summary = "Replace a category (omitting parentId makes it a root category)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> update(@PathVariable("categoryId") Long categoryId,
                                                @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success("Category updated", categoryService.updateCategory(categoryId, request));
    }

    @Operation(summary = "Delete an empty category", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> delete(@PathVariable("categoryId") Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.message("Category deleted");
    }
}
