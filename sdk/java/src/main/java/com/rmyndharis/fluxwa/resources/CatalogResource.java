package com.rmyndharis.fluxwa.resources;

import static com.rmyndharis.fluxwa.http.Http.encodeSegment;

import com.rmyndharis.fluxwa.FluxWaClient;
import com.rmyndharis.fluxwa.http.HttpMethod;
import com.rmyndharis.fluxwa.model.CatalogInfo;
import com.rmyndharis.fluxwa.model.CatalogProduct;
import com.rmyndharis.fluxwa.model.CatalogProductsQuery;
import com.rmyndharis.fluxwa.model.PaginatedProducts;
import com.rmyndharis.fluxwa.model.ProductMessageResponse;
import com.rmyndharis.fluxwa.model.SendProductRequest;

/**
 * Catalog resource — WhatsApp Business catalog, products, and product sends.
 *
 * <p>The catalog controller is mounted under the session root, so catalog reads are
 * {@code /catalog...} while product sends share the messages namespace
 * ({@code /messages/send-product}). Write operations
 * require an OPERATOR-level key.
 */
public final class CatalogResource {
    private final FluxWaClient client;

    public CatalogResource(FluxWaClient client) {
        this.client = client;
    }

    /** Get the business catalog info. */
    public CatalogInfo info(String sessionId) {
        return client.request(
            HttpMethod.GET, "/api/sessions/" + encodeSegment(sessionId) + "/catalog", null, null, CatalogInfo.class);
    }

    /** List catalog products. Returns a {@code { products, pagination }} page. */
    public PaginatedProducts products(String sessionId, CatalogProductsQuery query) {
        return client.request(
            HttpMethod.GET,
            "/api/sessions/" + encodeSegment(sessionId) + "/catalog/products",
            query,
            null,
            PaginatedProducts.class);
    }

    /** Get a single product by id. */
    public CatalogProduct product(String sessionId, String productId) {
        return client.request(
            HttpMethod.GET,
            "/api/sessions/" + encodeSegment(sessionId) + "/catalog/products/" + encodeSegment(productId),
            null,
            null,
            CatalogProduct.class);
    }

    /** Send a product message. Requires an OPERATOR-level key. Shares the messages path. */
    public ProductMessageResponse sendProduct(String sessionId, SendProductRequest body) {
        return client.request(
            HttpMethod.POST,
            "/api/sessions/" + encodeSegment(sessionId) + "/messages/send-product",
            null,
            body,
            ProductMessageResponse.class);
    }
}
