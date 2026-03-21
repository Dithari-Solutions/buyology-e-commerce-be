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
        PaymentProvider provider = providerRepo.findByName(PROVIDER_NAME)
                .orElseGet(() -> {
                    PaymentProvider p = new PaymentProvider();
                    p.setName(PROVIDER_NAME);
                    p.setBaseUrl(props.getBaseUrl());
                    p.setApiKey(props.getApiKey());
                    p.setHmacSecret(props.getHmacSecret());
                    p.setActive(true);
                    log.info("Seeding PaymentProvider: {}", PROVIDER_NAME);
                    return providerRepo.save(p);
                });

        seedMethod(provider, PaymentMethodType.CARD,
                props.getCard().getIntegrationId(),
                props.getCard().getIframeId());

        seedMethod(provider, PaymentMethodType.TABBY,
                props.getTabby().getIntegrationId(),
                null);

        seedMethod(provider, PaymentMethodType.TAMARA,
                props.getTamara().getIntegrationId(),
                null);
    }

    private void seedMethod(PaymentProvider provider,
                            PaymentMethodType type,
                            String integrationId,
                            String iframeId) {
        boolean exists = methodConfigRepo
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, type)
                .isPresent();

        if (!exists) {
            PaymentMethodConfig cfg = new PaymentMethodConfig();
            cfg.setProvider(provider);
            cfg.setMethodType(type);
            cfg.setIntegrationId(integrationId);
            cfg.setIframeId(iframeId);
            cfg.setCurrency("AED");
            cfg.setActive(true);
            methodConfigRepo.save(cfg);
            log.info("Seeded PaymentMethodConfig: {}", type);
        }
    }
}
