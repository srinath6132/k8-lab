package com.example.k8lab.cart;

import com.example.k8lab.common.BearerTokenFilter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final Map<String, Map<String, CartItem>> carts = new ConcurrentHashMap<>();
    private final RestClient products;

    public CartController(
            RestClient.Builder builder,
            @Value("${app.product-service.url}") String productServiceUrl) {
        this.products = builder.baseUrl(productServiceUrl).build();
    }

    @GetMapping
    public Cart getCart(@RequestAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE) String userId) {
        return cartFor(userId);
    }

    @PostMapping("/items")
    public Cart addItem(
            @RequestAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE) String userId,
            @RequestBody AddItem request) {
        if (request.quantity() < 1 || request.quantity() > 99) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be between 1 and 99");
        }
        Product product = fetchProduct(request.productId());
        Map<String, CartItem> items = carts.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        items.compute(product.id(), (ignored, current) -> {
            int quantity = request.quantity() + (current == null ? 0 : current.quantity());
            if (quantity > 99) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product quantity cannot exceed 99");
            }
            return new CartItem(product.id(), product.name(), product.imageUrl(), product.price(), quantity);
        });
        return cartFor(userId);
    }

    @DeleteMapping("/items/{productId}")
    public Cart removeItem(
            @RequestAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE) String userId,
            @PathVariable String productId) {
        carts.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).remove(productId);
        return cartFor(userId);
    }

    @DeleteMapping
    public Cart clearCart(@RequestAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE) String userId) {
        carts.remove(userId);
        return cartFor(userId);
    }

    private Product fetchProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID is required");
        }
        try {
            Product product = products.get().uri("/api/products/{id}", productId).retrieve().body(Product.class);
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product service returned an empty response");
            }
            return product;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
            throw exception;
        }
    }

    private Cart cartFor(String userId) {
        List<CartItem> items = new ArrayList<>(
                carts.getOrDefault(userId, Map.of()).values());
        items.sort(Comparator.comparing(CartItem::productId));
        BigDecimal total = items.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Cart(items, total);
    }

    public record AddItem(String productId, int quantity) {}
    public record Cart(List<CartItem> items, BigDecimal total) {}
    public record CartItem(String productId, String name, String imageUrl, BigDecimal price, int quantity) {}
    public record Product(
            String id, String name, String description, String category, BigDecimal price, String imageUrl) {}
}
