package com.example.k8lab.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;

class CartControllerTest {
    private static final String USER_ID = "9ac7e218-1374-4c93-8803-cb9aaee2832a";
    private static final String PRODUCT_URI = "http://products/api/products/camera-001";
    private static final String PRODUCT_JSON =
            "{\"id\":\"camera-001\",\"name\":\"Digital camera\","
                    + "\"description\":\"A compact camera\",\"category\":\"Electronics\","
                    + "\"imageUrl\":\"https://images.example/camera.jpg\",\"price\":349.00}";
    private MockRestServiceServer productService;
    private CartController carts;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        productService = MockRestServiceServer.bindTo(builder).build();
        carts = new CartController(builder, "http://products");
    }

    @Test
    void addsAndRemovesItemsFromOnlyTheAuthenticatedUsersCart() {
        expectCameraLookup();
        var cart = carts.addItem(USER_ID, new CartController.AddItem("camera-001", 2));
        assertEquals(2, cart.items().get(0).quantity());
        assertEquals("698.00", cart.total().toPlainString());

        expectCameraLookup();
        cart = carts.addItem(USER_ID, new CartController.AddItem("camera-001", 1));
        assertEquals(3, cart.items().get(0).quantity());
        assertEquals(0, carts.getCart("another-user").items().size());

        cart = carts.removeItem(USER_ID, "camera-001");
        assertEquals(0, cart.items().size());
        productService.verify();
    }

    @Test
    void rejectsInvalidQuantityBeforeCallingProductService() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> carts.addItem(USER_ID, new CartController.AddItem("camera-001", 0)));

        assertEquals(400, exception.getStatusCode().value());
        productService.verify();
    }

    private void expectCameraLookup() {
        productService.expect(requestTo(PRODUCT_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(PRODUCT_JSON, MediaType.APPLICATION_JSON));
    }
}
