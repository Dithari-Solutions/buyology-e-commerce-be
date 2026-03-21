package com.buyology.ecommerce.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paymob")
public class PaymobProperties {

    private String apiKey;
    private String hmacSecret;
    private String baseUrl;
    private Card card = new Card();
    private Method tabby = new Method();
    private Method tamara = new Method();

    public static class Card {
        private String integrationId;
        private String iframeId;

        public String getIntegrationId() { return integrationId; }
        public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }

        public String getIframeId() { return iframeId; }
        public void setIframeId(String iframeId) { this.iframeId = iframeId; }
    }

    public static class Method {
        private String integrationId;

        public String getIntegrationId() { return integrationId; }
        public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Card getCard() { return card; }
    public void setCard(Card card) { this.card = card; }

    public Method getTabby() { return tabby; }
    public void setTabby(Method tabby) { this.tabby = tabby; }

    public Method getTamara() { return tamara; }
    public void setTamara(Method tamara) { this.tamara = tamara; }
}
