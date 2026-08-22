package org.example.orderservice.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Wraps calls to the remote "ecomm" service. Kept as a separate Spring bean (rather than
 * private methods on OrderServiceImpl) so the @CircuitBreaker annotations are applied through
 * the Spring AOP proxy - self-invoked private methods would bypass it entirely.
 */
@Component
public class EcommClient {
    private static final String ECOMM_BASE_URL = "http://ecomm/api/v1";

    private final RestTemplate restTemplate;

    public EcommClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private HttpEntity<Void> authEntity(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        return new HttpEntity<>(headers);
    }

    @CircuitBreaker(name = "ecommService", fallbackMethod = "fetchCheckoutCartFallback")
    public CartCheckoutResponse fetchCheckoutCart(Long userId, String authHeader) {
        String url = ECOMM_BASE_URL + "/carts/user/" + userId + "/checkout-cart";
        ResponseEntity<ApiResponseWrapper<CartCheckoutResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, authEntity(authHeader), new ParameterizedTypeReference<>() {});
        return response.getBody() == null ? null : response.getBody().getData();
    }

    private CartCheckoutResponse fetchCheckoutCartFallback(Long userId, String authHeader, Throwable t) {
        throw new IllegalStateException("Cart service is currently unavailable, please try again later", t);
    }

    public void clearCart(Long cartId, String authHeader) {
        restTemplate.exchange(ECOMM_BASE_URL + "/carts/" + cartId + "/clear",
                HttpMethod.DELETE, authEntity(authHeader), Void.class);
    }

    @CircuitBreaker(name = "ecommService", fallbackMethod = "decreaseInventoryFallback")
    public void decreaseInventory(Long productId, int quantity) {
        String url = ECOMM_BASE_URL + "/products/product/" + productId + "/decrease-inventory?quantity=" + quantity;
        restTemplate.postForObject(url, null, ApiResponseWrapper.class);
    }

    private void decreaseInventoryFallback(Long productId, int quantity, Throwable t) {
        throw new IllegalStateException("Inventory service is currently unavailable for product " + productId, t);
    }
}
