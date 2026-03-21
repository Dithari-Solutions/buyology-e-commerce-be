package com.buyology.ecommerce.payment.config;

import com.buyology.ecommerce.payment.domain.PaymentMethodConfig;
import com.buyology.ecommerce.payment.domain.PaymentProvider;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.repository.PaymentMethodConfigRepository;
import com.buyology.ecommerce.payment.repository.PaymentProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the payment_providers and payment_method_configs tables on startup
 * if they do not already exist. Safe to run on every restart — idempotent.
 */
@Component
public class PaymentDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentDataInitializer.class);
    private static final String PROVIDER_NAME = "PAYMOB";

    private final PaymentProviderRepository providerRepo;
    private final PaymentMethodConfigRepository methodConfigRepo;
    private final PaymobProperties props;

    public PaymentDataInitializer(PaymentProviderRepository providerRepo,
                                  PaymentMethodConfigRepository methodConfigRepo,
                                  PaymobProperties props) {
        this.providerRepo = providerRepo;
        this.methodConfigRepo = methodConfigRepo;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Always sync credentials from env — handles rotated API keys without manual DB edits
        PaymentProvider provider = providerRepo.findByName(PROVIDER_NAME)
                .orElseGet(() -> {
                    PaymentProvider p = new PaymentProvider();
                    p.setName(PROVIDER_NAME);
                    p.setActive(true);
                    log.info("Creating PaymentProvider: {}", PROVIDER_NAME);
                    return p;
                });

        String apiKey = props.getApiKey();
        // Log prefix/suffix to diagnose key truncation or wrong value — never logs the full key
        if (apiKey != null && apiKey.length() > 10) {
            log.info("Paymob API key loaded: starts='{}' ends='{}' length={}",
                    apiKey.substring(0, 6), apiKey.substring(apiKey.length() - 4), apiKey.length());
        } else {
            log.warn("Paymob API key is missing or too short — check PAYMOB_API_KEY env var");
        }

        provider.setBaseUrl(props.getBaseUrl());
        provider.setApiKey(apiKey);
        provider.setHmacSecret(props.getHmacSecret());
        providerRepo.save(provider);

        upsertMethod(provider, PaymentMethodType.CARD,
                props.getCard().getIntegrationId(),
                props.getCard().getIframeId());

        upsertMethod(provider, PaymentMethodType.TABBY,
                props.getTabby().getIntegrationId(),
                null);

        upsertMethod(provider, PaymentMethodType.TAMARA,
                props.getTamara().getIntegrationId(),
                null);
    }

    private void upsertMethod(PaymentProvider provider,
                               PaymentMethodType type,
                               String integrationId,
                               String iframeId) {
        PaymentMethodConfig cfg = methodConfigRepo
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, type)
                .orElseGet(() -> {
                    PaymentMethodConfig c = new PaymentMethodConfig();
                    c.setProvider(provider);
                    c.setMethodType(type);
                    c.setCurrency("AED");
                    c.setActive(true);
                    log.info("Creating PaymentMethodConfig: {}", type);
                    return c;
                });

        cfg.setIntegrationId(integrationId);
        cfg.setIframeId(iframeId);
        methodConfigRepo.save(cfg);
    }
}
