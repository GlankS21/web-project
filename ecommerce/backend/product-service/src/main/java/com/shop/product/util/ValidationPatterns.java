package com.shop.product.util;

/**
 * Regular expressions of the request DTOs. The {@code *_MESSAGE} constants are Bean Validation
 * message keys resolved from {@code messages_ru.properties}.
 */
public final class ValidationPatterns {

    /** Upper-case letters, digits and dashes, 3 to 64 characters, e.g. PHN-IP15-128. */
    public static final String SKU = "^[A-Z0-9][A-Z0-9-]{2,63}$";
    public static final String SKU_MESSAGE = "{validation.sku}";

    /** Lower-case ASCII words separated by single dashes, e.g. smartfon-apple-iphone-15-128-gb. */
    public static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    public static final String SLUG_MESSAGE = "{validation.slug}";

    public static final String HTTP_URL = "^https?://\\S+$";
    public static final String HTTP_URL_MESSAGE = "{validation.http-url}";

    /** Name of an uploaded image: a random lower-case UUID and the extension of its detected type. */
    public static final String IMAGE_FILENAME_BODY =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|png|webp|gif)";
    public static final String IMAGE_FILENAME = "^" + IMAGE_FILENAME_BODY + "$";

    /** Path under which product-service serves uploaded images (relative, same origin as the API). */
    public static final String UPLOADED_IMAGE_PATH = "/api/products/images/";

    /** An absolute http(s) URL, or the relative URL of an image uploaded to product-service. */
    public static final String IMAGE_URL = "^(?:https?://\\S+|" + UPLOADED_IMAGE_PATH + IMAGE_FILENAME_BODY + ")$";
    public static final String IMAGE_URL_MESSAGE = "{validation.image-url}";

    private ValidationPatterns() {
    }
}
