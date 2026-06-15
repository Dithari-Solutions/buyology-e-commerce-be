package com.buyology.ecommerce.currency.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Public, read-only currency conversion using live FX rates. Used by the
 * storefront (e.g. to show the free-shipping threshold in the shopper's currency).
 */
@RestController
@RequestMapping("/api/currency")
public class CurrencyController {

    private final CurrencyExchangeService currencyExchangeService;

    public CurrencyController(CurrencyExchangeService currencyExchangeService) {
        this.currencyExchangeService = currencyExchangeService;
    }

    /** GET /api/currency/convert?amount=100&from=AED&to=AZN */
    @GetMapping("/convert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        BigDecimal converted = currencyExchangeService.convert(amount, from, to);
        return ApiResponse.success(
                Map.of("amount", converted, "currency", to.toUpperCase()),
                "Converted");
    }
}
