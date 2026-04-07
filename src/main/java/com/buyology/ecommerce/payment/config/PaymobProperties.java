package com.buyology.ecommerce.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paymob")
public class PaymobProperties {

    private String apiKey;           // legacy — no longer used
    private String secretKey;        // Intention API: Authorization: Token header
    private String publicKey;        // Intention API: frontend checkout URL
    private String hmacSecret;
    private String baseUrl;
    private String notificationUrl;  // Paymob will POST transaction webhooks to this URL
    private String redirectionUrl;   // Paymob will redirect the user browser to this URL
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

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getNotificationUrl() { return notificationUrl; }
    public void setNotificationUrl(String notificationUrl) { this.notificationUrl = notificationUrl; }

    public String getRedirectionUrl() { return redirectionUrl; }
    public void setRedirectionUrl(String redirectionUrl) { this.redirectionUrl = redirectionUrl; }

    public Card getCard() { return card; }
    public void setCard(Card card) { this.card = card; }

    public Method getTabby() { return tabby; }
    public void setTabby(Method tabby) { this.tabby = tabby; }

    public Method getTamara() { return tamara; }
    public void setTamara(Method tamara) { this.tamara = tamara; }
}
