package com.shop.product.service.impl;

import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ConflictException;
import com.shop.product.dto.request.CategoryRequest;
import com.shop.product.dto.response.CategoryResponse;
import com.shop.product.entity.Category;
import com.shop.product.mapper.CategoryMapperImpl;
import com.shop.product.repository.CategoryRepository;
import com.shop.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryRepository, productRepository, new CategoryMapperImpl());
    }

    @Test
    void getTree_nestsChildrenUnderTheirRoots() {
        Category phones = category(1L, "Phones", null);
        Category laptops = category(4L, "Laptops", null);
        Category smartphones = category(2L, "Smartphones", phones);
        when(categoryRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(phones, smartphones, laptops));

        List<CategoryResponse> tree = service.getTree();

        assertThat(tree).extracting(CategoryResponse::id).containsExactly(1L, 4L);
        assertThat(tree.getFirst().children()).extracting(CategoryResponse::id).containsExactly(2L);
        assertThat(tree.getFirst().children().getFirst().parentId()).isEqualTo(1L);
        assertThat(tree.get(1).children()).isEmpty();
    }

    @Test
    void createCategory_derivesSlugFromVietnameseName() {
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = service.createCategory(new CategoryRequest("Đồng hồ thông minh", null, null, null, null));

        assertThat(response.slug()).isEqualTo("dong-ho-thong-minh");
        assertThat(response.parentId()).isNull();
    }

    @Test
    void createCategory_rejectsSlugInUse() {
        when(categoryRepository.existsBySlug("laptop")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(new CategoryRequest("Laptop", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createCategory_refusesThirdLevel() {
        Category root = category(1L, "Phones", null);
        Category child = category(2L, "Smartphones", root);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.createCategory(new CategoryRequest("Foldables", null, null, 2L, null)))
                .isInstanceOf(BusinessException.class);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_unknownParentIsBadRequest() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCategory(new CategoryRequest("X", null, null, 99L, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateCategory_rootWithChildrenCannotMoveUnderAnotherRoot() {
        Category phones = category(1L, "Phones", null);
        Category laptops = category(4L, "Laptops", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(phones));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(laptops));
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateCategory(1L, new CategoryRequest("Phones", null, null, 4L, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateCategory_cannotBeItsOwnParent() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Phones", null)));

        assertThatThrownBy(() -> service.updateCategory(1L, new CategoryRequest("Phones", null, null, 1L, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteCategory_refusedWhileItHasProducts() {
        Category category = category(2L, "Smartphones", category(1L, "Phones", null));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCategory(2L)).isInstanceOf(BusinessException.class);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategory_removesEmptyCategory() {
        Category category = category(9L, "Empty", null);
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(category));

        service.deleteCategory(9L);

        verify(categoryRepository).delete(category);
    }

    private Category category(Long id, String name, Category parent) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(name.toLowerCase());
        category.setParent(parent);
        return category;
    }
}
