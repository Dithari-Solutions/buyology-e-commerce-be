package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.dto.CreateOrderRequest;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutCountryTest {
    private final Object[] dependencies;
    private final OrderService service;
    private final UUID uid = UUID.randomUUID(), credentialId = UUID.randomUUID();
    private final Cart cart = new Cart();
    private final UserProfiles profile = new UserProfiles();
    private final UserAddress address = new UserAddress();
    private final CreateOrderRequest request = new CreateOrderRequest();

    CheckoutCountryTest() throws Exception {
        Constructor<?> ctor = OrderService.class.getDeclaredConstructors()[0];
        dependencies = new Object[ctor.getParameterCount()];
        for (int i = 0; i < dependencies.length; i++) {
            Class<?> type = ctor.getParameterTypes()[i];
            dependencies[i] = type == ObjectMapper.class ? new ObjectMapper()
                    : type.isPrimitive() ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0) : mock(type);
        }
        service = (OrderService) ctor.newInstance(dependencies);
        Users user = new Users(); user.setId(uid); user.setFirstName("Customer");
        AuthCredentials credential = new AuthCredentials(); credential.setId(credentialId); credential.setUserId(uid);
        cart.setId(UUID.randomUUID()); cart.setAuthCredential(credential); cart.setCountryCode("AZE");
        cart.setCurrency("AZN"); cart.setStatus(Cart.CartStatus.CHECKED_OUT); cart.setTotalPrice(new BigDecimal("1000"));
        profile.setUser(user); profile.setPhoneNumber("+994501234567"); profile.setSelectedCountryCode("UAE");
        address.setId(UUID.randomUUID()); address.setUser(user); address.setCountry("AZ"); address.setCity("Baku");
        Product product = new Product(); product.setId(UUID.randomUUID()); product.setSku("LAPTOP");
        Store store = new Store(); store.setId(UUID.randomUUID());
        StoreProduct listing = new StoreProduct(); listing.setProduct(product); listing.setStore(store); listing.setStorePrice(new BigDecimal("1000"));
        CartItem line = new CartItem(); line.setId(UUID.randomUUID()); line.setCart(cart); line.setProduct(product);
        line.setStoreId(store.getId()); line.setQuantity(1); line.setSelected(true); line.setUnitPrice(new BigDecimal("1000")); line.setTotalPrice(new BigDecimal("1000"));
        request.setCartId(cart.getId()); request.setAddressId(address.getId()); request.setDeliveryMethod(DeliveryMethod.REGULAR);
        when(dependency(CartRepository.class).findById(cart.getId())).thenReturn(Optional.of(cart));
        when(dependency(CartItemRepository.class).findByCartIdAndSelectedTrue(cart.getId())).thenReturn(List.of(line));
        when(dependency(StoreProductRepository.class).findActiveByStoreIdsAndProductIds(any(), any())).thenReturn(List.of(listing));
        when(dependency(UserProfilesRepository.class).findByUserId(uid)).thenReturn(Optional.of(profile));
        when(dependency(UserAddressRepository.class).findById(address.getId())).thenReturn(Optional.of(address));
        when(dependency(CurrencyExchangeService.class).convert(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(dependency(DeliveryFeePolicy.class).feeAed(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(dependency(VatPolicy.class).vatIncludedIn(any(), any())).thenReturn(BigDecimal.ZERO);
        when(dependency(OrderRepository.class).save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0); if (order.getId() == null) order.setId(UUID.randomUUID()); return order;
        });
    }
    private <T> T dependency(Class<T> type) {
        for (Object value : dependencies) if (type.isInstance(value)) return type.cast(value);
        throw new IllegalArgumentException(type.getName());
    }
    @Test void azerbaijanCartAndAddressWorkWithStaleUaeBrowsingPreference() {
        var order = service.createOrder(uid, credentialId, request);
        assertEquals("AZE", order.getCountryCode()); assertEquals("AZ", order.getCountry());
        assertEquals("AZN", order.getCurrency()); assertEquals(new BigDecimal("1000"), order.getTotalAmount());
    }
    @Test void stalePreferenceCannotMakeUaeGoodsShipToAzerbaijan() {
        cart.setCountryCode("UAE"); profile.setSelectedCountryCode("AZE");
        var error = assertThrows(IllegalArgumentException.class, () -> service.createOrder(uid, credentialId, request));
        assertTrue(error.getMessage().contains("cart is for delivery in UAE"));
        verify(dependency(OrderRepository.class), never()).save(any());
    }
    @Test void changingProfileCountryAfterCaptureDoesNotLoseAnAzerbaijanOrder() {
        profile.setPhoneNumber(null);
        assertEquals("AZE", service.createOrder(uid, credentialId, request, OrderService.CapturePhase.AFTER_CAPTURE).getCountryCode());
    }
    @Test void anOldAddressUsesTheContactCompletedAtCheckout() {
        address.setFirstName(" "); address.setPhoneNumber("");
        var order = service.createOrder(uid, credentialId, request);
        assertEquals("Customer", order.getRecipientFirstName());
        assertEquals("+994501234567", order.getRecipientPhone());
    }
    @Test void anExplicitDeliveryRecipientKeepsTheirOwnContact() {
        address.setFirstName("Recipient"); address.setPhoneNumber("+994509999999");
        var order = service.createOrder(uid, credentialId, request);
        assertEquals("Recipient", order.getRecipientFirstName()); assertEquals("+994509999999", order.getRecipientPhone());
    }
}
