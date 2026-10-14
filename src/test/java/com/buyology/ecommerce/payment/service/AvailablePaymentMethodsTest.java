package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.domain.PaymentProvider;
import com.buyology.ecommerce.payment.domain.PaymentMethodConfig;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.repository.PaymentProviderRepository;
import com.buyology.ecommerce.payment.repository.PaymentMethodConfigRepository;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import java.math.BigDecimal;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.payment.dto.InitiatePaymentRequest;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.order.repository.OrderRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvailablePaymentMethodsTest {
    private final Object[] dependencies;
    private final PaymentService service;
    private final PaymentProvider provider = new PaymentProvider();

    AvailablePaymentMethodsTest() throws Exception {
        Constructor<?> ctor = PaymentService.class.getDeclaredConstructors()[0];
        dependencies = new Object[ctor.getParameterCount()];
        for (int i = 0; i < dependencies.length; i++) dependencies[i] = mock(ctor.getParameterTypes()[i]);
        service = (PaymentService) ctor.newInstance(dependencies);
        provider.setSecretKey("test-secret"); provider.setPublicKey("test-public");
        provider.setHmacSecret("test-hmac"); provider.setBaseUrl("https://uae.paymob.com");
        when(dependency(PaymentProviderRepository.class).findFirstByIsActiveTrue()).thenReturn(Optional.of(provider));
    }

    private <T> T dependency(Class<T> type) {
        for (Object value : dependencies) if (type.isInstance(value)) return type.cast(value);
        throw new IllegalArgumentException(type.getName());
    }

    private void configured(PaymentMethodType type, String integration, String currency) {
        PaymentMethodConfig config = new PaymentMethodConfig();
        config.setProvider(provider); config.setMethodType(type); config.setIntegrationId(integration); config.setCurrency(currency);
        when(dependency(PaymentMethodConfigRepository.class).findByProviderAndMethodTypeAndIsActiveTrue(provider, type))
                .thenReturn(Optional.of(config));
    }

    @Test void offersOnlyConfiguredMethodsWithoutExposingCredentials() {
        configured(PaymentMethodType.CARD, "12345", "AED");
        assertEquals(List.of(PaymentMethodType.CARD), service.availablePaymentMethods().methods());
        assertEquals("AED", service.availablePaymentMethods().currency());
        assertFalse(service.availablePaymentMethods().toString().contains("test-secret"));
    }
    @Test void omitsMethodsThatCannotBePassedToTheIntentionApi() {
        configured(PaymentMethodType.CARD, "invalid", "AED");
        configured(PaymentMethodType.TABBY, "23456", "USD");
        configured(PaymentMethodType.TAMARA, "99999999999999999999", "AED");
        assertTrue(service.availablePaymentMethods().methods().isEmpty());
    }
    @Test void unavailableProviderOffersNoOnlineMethods() {
        when(dependency(PaymentProviderRepository.class).findFirstByIsActiveTrue()).thenReturn(Optional.empty());
        assertTrue(service.availablePaymentMethods().methods().isEmpty());
    }
    @Test void missingSettlementCredentialsDoNotAdvertiseCheckout() {
        configured(PaymentMethodType.CARD, "12345", "AED"); provider.setHmacSecret("");
        assertTrue(service.availablePaymentMethods().methods().isEmpty());
    }
    @Test void azerbaijanQuoteUsesTheSameAedConversionAsPaymentInitiation() {
        when(dependency(CurrencyExchangeService.class).convert(new BigDecimal("1000"), "AZN", "AED"))
                .thenReturn(new BigDecimal("2160.50"));
        var quote = service.quotePayment(new BigDecimal("1000"), "azn");
        assertEquals(new BigDecimal("2160.50"), quote.amount()); assertEquals("AED", quote.currency());
    }
    @Test void malformedQuotesNeverReachTheFxProvider() {
        assertThrows(IllegalArgumentException.class, () -> service.quotePayment(BigDecimal.ONE, "not-a-currency"));
        assertThrows(IllegalArgumentException.class, () -> service.quotePayment(BigDecimal.ONE.negate(), "AZN"));
        verifyNoInteractions(dependency(CurrencyExchangeService.class));
    }
    @SuppressWarnings("unchecked")
    @Test void aMissingOrderCannotCreateAnUnmatchedGatewayCharge() {
        var request = new InitiatePaymentRequest(); request.setAppOrderId(UUID.randomUUID());
        var orders = mock(OrderRepository.class);
        ObjectProvider<OrderRepository> orderProvider = (ObjectProvider<OrderRepository>) ReflectionTestUtils.getField(service, "orderRepoProvider");
        when(orderProvider.getObject()).thenReturn(orders);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentUserId).thenReturn(UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, () -> service.initiatePayment(request));
        }
        verifyNoInteractions(dependency(PaymobClient.class), dependency(PaymentTransactionRepository.class));
    }
    @SuppressWarnings("unchecked")
    @Test void azerbaijanCardPaymentChargesTheOrderTotalInAedAndKeepsItsPhoneAndReference() {
        configured(PaymentMethodType.CARD, "12345", "AED");
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        UUID uid = UUID.randomUUID(), credential = UUID.randomUUID(), txId = UUID.randomUUID();
        Order order = new Order(); order.setId(UUID.randomUUID()); order.setUserId(uid);
        order.setAuthCredentialId(credential); order.setCartId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING_PAYMENT); order.setCurrency("AZN");
        order.setTotalAmount(new BigDecimal("1000")); order.setCountry("AZE");
        order.setRecipientPhone("+994501234567"); order.setCity("Baku"); order.setAddressLine1("Baku delivery");
        var orders = mock(OrderRepository.class);
        ObjectProvider<OrderRepository> orderProvider = (ObjectProvider<OrderRepository>) ReflectionTestUtils.getField(service, "orderRepoProvider");
        when(orderProvider.getObject()).thenReturn(orders);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(dependency(CurrencyExchangeService.class).convert(new BigDecimal("1000"), "AZN", "AED"))
                .thenReturn(new BigDecimal("2160.50"));
        PaymentTransaction[] saved = new PaymentTransaction[1];
        when(dependency(PaymentTransactionRepository.class).save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0); tx.setId(txId); saved[0] = tx; return tx;
        });
        when(dependency(PaymentTransactionRepository.class).findById(txId)).thenAnswer(inv -> Optional.ofNullable(saved[0]));
        when(dependency(PaymobClient.class).createIntention(anyString(), anyString(), anyLong(), anyString(), anyInt(), anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    assertEquals(216050L, (long) inv.getArgument(2));
                    assertEquals("AED", inv.getArgument(3)); assertEquals(txId.toString(), inv.getArgument(5));
                    ObjectNode billing = inv.getArgument(6);
                    assertEquals("+994501234567", billing.path("phone_number").asText());
                    return new PaymobClient.IntentionResult("test-intention", "test-client-secret", 123456789L);
                });
        var request = new InitiatePaymentRequest(); request.setAppOrderId(order.getId()); request.setCartId(order.getCartId());
        request.setMethodType(PaymentMethodType.CARD); request.setCurrency("AZN");
        request.setAmount(new BigDecimal("100")); // A client price cannot reduce the authoritative order total.
        request.setCustomerId(UUID.randomUUID()); request.setCustomerEmail("qa@example.com"); request.setBillingName("QA Customer");
        request.setRedirectionUrl("https://az.buyology.online/payment/callback");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentUserId).thenReturn(uid);
            assertNotNull(service.initiatePayment(request).getCheckoutUrl());
        }
        assertEquals(order.getId(), saved[0].getAppOrderId()); assertEquals(credential, saved[0].getCustomerId());
        assertEquals(new BigDecimal("2160.50"), saved[0].getAmount()); assertEquals("AED", saved[0].getCurrency());
        assertEquals("+994501234567", saved[0].getCustomerPhone()); assertEquals(123456789L, saved[0].getPaymobOrderId());
    }
}
