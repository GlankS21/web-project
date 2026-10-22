package com.shop.product.mapper;

import com.shop.product.dto.request.ProductImageRequest;
import com.shop.product.dto.request.ProductRequest;
import com.shop.product.dto.response.CategorySummaryResponse;
import com.shop.product.dto.response.ProductImageResponse;
import com.shop.product.dto.response.ProductResponse;
import com.shop.product.dto.response.ProductSummaryResponse;
import com.shop.product.entity.Category;
import com.shop.product.entity.Product;
import com.shop.product.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * The currency is a store-wide setting, passed in by the service. SKU, slug, category, status and
 * images are resolved by the service, never copied from the request; the rating summary is maintained
 * by the review service.
 */
@Mapper
public interface ProductMapper {

    CategorySummaryResponse toCategorySummary(Category category);

    @Mapping(target = "currency", source = "currency")
    ProductResponse toResponse(Product product, String currency);

    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "thumbnailUrl", expression = "java(product.thumbnailUrl())")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    ProductSummaryResponse toSummary(Product product, String currency);

    ProductImageResponse toImageResponse(ProductImage image);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    ProductImage toImageEntity(ProductImageRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "ratingAverage", ignore = true)
    @Mapping(target = "ratingCount", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(ProductRequest request, @MappingTarget Product product);
}
