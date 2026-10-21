package com.shop.common.web;

import com.shop.common.exception.ConflictException;
import com.shop.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AbstractGlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new TestExceptionHandler())
                .build();
    }

    @Test
    void resourceNotFound_returns404WithErrorBody() throws Exception {
        mockMvc.perform(get("/items/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Item not found with id '42'"))
                .andExpect(jsonPath("$.path").value("/items/42"));
    }

    @Test
    void conflict_returns409() throws Exception {
        mockMvc.perform(get("/items/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void invalidBody_returns400WithViolations() throws Exception {
        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.length()").value(2));
    }

    @Test
    void malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unexpectedException_returns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/items/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestControllerAdvice
    static class TestExceptionHandler extends AbstractGlobalExceptionHandler {
    }

    @RestController
    static class TestController {

        @GetMapping("/items/conflict")
        String conflict() {
            throw new ConflictException("Item already exists");
        }

        @GetMapping("/items/boom")
        String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/items/{id}")
        String find(@PathVariable("id") Long id) {
            throw new ResourceNotFoundException("Item", "id", id);
        }

        @PostMapping("/items")
        String create(@Valid @RequestBody CreateItemRequest request) {
            return "created";
        }
    }

    record CreateItemRequest(@NotBlank String name, @Email String email) {
    }
}
