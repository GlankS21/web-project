package com.shop.product.mapper;

import com.shop.product.dto.request.CategoryRequest;
import com.shop.product.dto.response.CategoryResponse;
import com.shop.product.dto.response.CategorySummaryResponse;
import com.shop.product.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Slug, parent and sort order are resolved by the service, never copied from the request.
 */
@Mapper
public interface CategoryMapper {

    @Mapping(target = "parentId", source = "category.parent.id")
    @Mapping(target = "children", source = "children")
    CategoryResponse toResponse(Category category, List<CategoryResponse> children);

    default CategoryResponse toResponse(Category category) {
        return toResponse(category, List.of());
    }

    CategorySummaryResponse toSummary(Category category);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(CategoryRequest request, @MappingTarget Category category);
}
