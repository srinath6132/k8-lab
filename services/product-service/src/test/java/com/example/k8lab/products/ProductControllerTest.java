package com.example.k8lab.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductControllerTest {
    private final ProductController products = new ProductController();

    @Test
    void searchesNameAndReturnsImageUrl() {
        var matches = products.search("camera", "");

        assertEquals(1, matches.size());
        assertEquals("camera-001", matches.get(0).id());
        assertEquals("https://images.unsplash.com/photo-1516035069371-29a1b244cc32", matches.get(0).imageUrl());
    }

    @Test
    void filtersByCategoryIgnoringCase() {
        assertEquals(2, products.search("", "hOmE").size());
    }

    @Test
    void includesKiranaEssentialsWithImages() {
        var matches = products.search("rice", "");

        assertEquals(1, matches.size());
        assertEquals("rice-001", matches.get(0).id());
        assertEquals("Staples", matches.get(0).category());
        assertTrue(matches.get(0).imageUrl().startsWith("https://"));
    }
}
