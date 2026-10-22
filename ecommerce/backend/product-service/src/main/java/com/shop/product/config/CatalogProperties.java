package com.shop.product.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * @param currency         ISO 4217 code of every catalogue price (single-currency store)
 * @param imageStorageDir  directory of the uploaded product images, created at startup when missing
 * @param imageMaxSize     largest accepted image upload; {@code spring.servlet.multipart.max-file-size}
 *                         must be slightly larger so that the service reports the limit itself
 */
@Validated
@ConfigurationProperties(prefix = "app.catalog")
public record CatalogProperties(
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        String imageStorageDir,
        DataSize imageMaxSize
) {

    public static final String DEFAULT_IMAGE_STORAGE_DIR = "./data/product-images";
    public static final DataSize DEFAULT_IMAGE_MAX_SIZE = DataSize.ofMegabytes(5);

    public CatalogProperties {
        currency = currency == null ? "RUB" : currency;
        imageStorageDir = imageStorageDir == null || imageStorageDir.isBlank()
                ? DEFAULT_IMAGE_STORAGE_DIR : imageStorageDir;
        imageMaxSize = imageMaxSize == null ? DEFAULT_IMAGE_MAX_SIZE : imageMaxSize;
    }
}
