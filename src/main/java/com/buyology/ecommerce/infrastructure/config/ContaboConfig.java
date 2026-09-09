package com.buyology.ecommerce.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
public class ContaboConfig {

    private final ContaboProperties properties;

    public ContaboConfig(ContaboProperties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .region(Region.US_EAST_1) // Contabo S3 is region-agnostic but AWS SDK requires a region
                .forcePathStyle(true)      // Required for many S3-compatible providers
                // Overall call + per-attempt timeouts so a stalled storage backend
                // can never hang a request thread indefinitely. (Applied at the SDK
                // layer so no specific HTTP-client artifact is required.)
                .overrideConfiguration(b -> b
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
    }

    /**
     * Presigner for GET URLs that are handed to a browser.
     *
     * <p>Signs for {@code contabo.s3.cdn-url} when it is set, falling back to the object store's
     * own endpoint when it is not, so this is a no-op until the CDN hostname is configured.
     *
     * <p>Why sign for the CDN host rather than rewrite the host afterwards: SigV4 covers the Host
     * header, so a URL signed for the origin and served under another name fails with
     * SignatureDoesNotMatch unless the edge rewrites Host back — which needs an origin rule or a
     * worker. Signing for the hostname the browser will actually use removes that requirement
     * entirely; the request arrives at Ceph with the Host it was signed with and validates.
     *
     * <p>This relies on the store accepting a Host it does not own. These URLs are path-style
     * (the bucket is in the path, not the hostname), which Ceph normally validates against
     * whatever Host arrives — but a deployment with rgw_dns_name set may reject it. If it does,
     * the symptom is SignatureDoesNotMatch and the answer is an edge Host rewrite instead.
     */
    @Bean
    public S3Presigner cdnPresigner() {
        String cdn = properties.getCdnUrl();
        String endpoint = (cdn == null || cdn.isBlank()) ? properties.getEndpoint() : cdn;
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /** Origin presigner. Uploads keep using this — a PUT should go direct, not through an edge. */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true) // Crucial for Contabo S3 Presigning
                        .build())
                .build();
    }
}
