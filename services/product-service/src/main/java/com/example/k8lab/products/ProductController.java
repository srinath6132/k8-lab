package com.example.k8lab.products;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final List<Product> products = List.of(
            new Product("headphones-001", "Wireless headphones", "Over-ear noise cancelling headphones", "Electronics",
                    new BigDecimal("79.99"), "https://images.unsplash.com/photo-1505740420928-5e560c06d30e"),
            new Product("camera-001", "Digital camera", "Compact camera for travel and everyday photography", "Electronics",
                    new BigDecimal("349.00"), "https://images.unsplash.com/photo-1516035069371-29a1b244cc32"),
            new Product("backpack-001", "Everyday backpack", "Water-resistant backpack with a laptop sleeve", "Accessories",
                    new BigDecimal("54.50"), "https://images.unsplash.com/photo-1553062407-98eeb64c6a62"),
            new Product("lamp-001", "Desk lamp", "Adjustable LED lamp for a home office", "Home",
                    new BigDecimal("32.00"), "https://images.unsplash.com/photo-1507473885765-e6ed057f782c"),
            new Product("coffee-001", "Coffee maker", "Programmable drip coffee maker", "Home",
                    new BigDecimal("89.95"), "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085"),
            new Product("shoes-001", "Running shoes", "Lightweight daily running shoes", "Fashion",
                    new BigDecimal("110.00"), "https://images.unsplash.com/photo-1542291026-7eec264c27ff"));
    private final Map<String, Product> productsById =
            products.stream().collect(Collectors.toUnmodifiableMap(Product::id, Function.identity()));

    @GetMapping
    public List<Product> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category) {
        String query = q.toLowerCase(Locale.ROOT).trim();
        String selectedCategory = category.toLowerCase(Locale.ROOT).trim();
        return products.stream()
                .filter(product -> selectedCategory.isEmpty()
                        || product.category().toLowerCase(Locale.ROOT).equals(selectedCategory))
                .filter(product -> query.isEmpty()
                        || (product.name() + " " + product.description() + " " + product.category())
                                .toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable String id) {
        Product product = productsById.get(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return product;
    }

    public record Product(
            String id, String name, String description, String category, BigDecimal price, String imageUrl) {}
}
