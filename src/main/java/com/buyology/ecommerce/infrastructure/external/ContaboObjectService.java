package com.buyology.ecommerce.infrastructure.external;

import com.buyology.ecommerce.infrastructure.config.ContaboProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ContaboObjectService {

    // Object-key prefixes whose images we brand with the Buyology watermark.
    // ONLY product catalog images are watermarked. Story, banner, news (marketing/
    // editorial) and users/ (avatars), refunds/ (evidence), documents (licenses)
    // must NOT be altered.
    private static final List<String> WATERMARK_PREFIXES = List.of("products/");

    /**
     * Key prefixes whose URLs are held steady rather than re-signed per call.
     *
     * <p>These are the images every visitor is shown, and a signature that changes on each
     * render defeats every cache between the object store and the screen. The home banner was
     * the clearest case: signed fresh on every page render, so the largest image on the busiest
     * page was downloaded from storage and re-encoded for each visitor, every time.
     *
     * <p>They are all replaceable at the same key, which is what kept them off this list
     * before. That is handled properly now — {@link #evictPresignedUrl(String)} drops the
     * cached URL the moment an object is overwritten or deleted.
     */
    private static final List<String> CACHEABLE_PREFIXES =
            List.of("products/", "banners/", "stories/", "news/");

    // Presigned GET URLs are signed with the current timestamp, so a fresh call
    // produces a DIFFERENT URL for the same object every time — which busts the
    // browser's image cache on every page load. We sign once and reuse the same
    // URL for a window comfortably shorter than the signature validity, so the
    // URL stays byte-identical across requests and the browser can cache the
    // image. The signed request also carries Cache-Control so the bytes are
    // actually cached client-side.
    private static final Duration PRESIGN_SIGNATURE_TTL = Duration.ofHours(6);
    private static final Duration PRESIGN_CACHE_TTL = Duration.ofHours(4);
    private static final String IMAGE_CACHE_CONTROL =
            "public, max-age=" + Duration.ofHours(6).toSeconds();
    private static final int PRESIGN_CACHE_MAX_ENTRIES = 20_000;

    /**
     * Shared presign cache, so every host signs a product image to the SAME url.
     *
     * <p>The in-memory cache below keeps a url stable on ONE host, which is all it can do.
     * We run two, and the load balancer alternates between them, so the same photo was being
     * handed out under two different signatures — and everything downstream keys on the full
     * url. Two browser cache entries, two Cloudflare entries, two image-optimizer encodes, for
     * one photo. A restart re-signed everything and lost the lot again.
     *
     * <p>Redis makes the signature a shared decision instead of a per-host one: whoever signs
     * first wins, everyone else serves the winner's url, and it survives a deploy. The version
     * suffix is here so a change to how we sign can invalidate the old entries by rename.
     */
    private static final String PRESIGN_REDIS_PREFIX = "img:presign:v1:";
    /** A url from Redis whose remaining lifetime we cannot read: hold it briefly, then re-ask. */
    private static final Duration PRESIGN_UNKNOWN_TTL = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(ContaboObjectService.class);

    private record CachedUrl(String url, Instant expiresAt) {}

    private final Map<String, CachedUrl> presignedUrlCache = new ConcurrentHashMap<>();

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ContaboProperties properties;
    private final WatermarkService watermarkService;
    private final StringRedisTemplate redis;

    public ContaboObjectService(S3Client s3Client, S3Presigner s3Presigner,
                                ContaboProperties properties, WatermarkService watermarkService,
                                StringRedisTemplate redis) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.watermarkService = watermarkService;
        this.redis = redis;
    }

    /**
     * Uploads a file to Contabo S3 and returns the S3 key.
     * Path format: products/{uuid}/{filename}
     *
     * Catalog/marketing images (see {@link #WATERMARK_PREFIXES}) are stamped with
     * the Buyology logo before upload. If watermarking changes the format (e.g.
     * WebP→PNG) the returned key's extension is updated to match, so callers that
     * persist the returned key stay consistent. Watermarking fails open: any issue
     * uploads the original bytes unchanged.
     */
    public String uploadFile(String key, MultipartFile file) {
        try {
            if (shouldWatermark(key, file.getContentType())) {
                byte[] original = file.getBytes();
                var watermarked = watermarkService.apply(original, file.getContentType());
                if (watermarked.isPresent()) {
                    String finalKey = withExtension(key, watermarked.get().extension());
                    putBytes(finalKey, watermarked.get().bytes(), watermarked.get().contentType());
                    return finalKey;
                }
                // Not processed (unsupported format / decode failure) — upload as-is.
                putBytes(key, original, file.getContentType());
                return key;
            }

            // Non-watermarked path (videos, avatars, documents, …): stream directly
            // so large files aren't buffered into memory.
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return key; // Return the KEY instead of the URL
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to Contabo S3: " + key, e);
        }
    }

    private boolean shouldWatermark(String key, String contentType) {
        if (!watermarkService.isEnabled() || key == null || contentType == null) {
            return false;
        }
        if (!contentType.toLowerCase().startsWith("image/")) {
            return false; // never touch videos or documents
        }
        return WATERMARK_PREFIXES.stream().anyMatch(key::startsWith);
    }

    /** Replaces the key's file extension (e.g. on WebP→PNG re-encode). Keeps it if unchanged. */
    private static String withExtension(String key, String newExt) {
        int slash = key.lastIndexOf('/');
        int dot = key.lastIndexOf('.');
        if (dot <= slash) {
            return key + "." + newExt; // no existing extension
        }
        String current = key.substring(dot + 1).toLowerCase();
        if (current.equals(newExt.toLowerCase())) {
            return key;
        }
        return key.substring(0, dot + 1) + newExt;
    }

    private void putBytes(String key, byte[] data, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
        evictPresignedUrl(key);
    }

    /**
     * Uploads raw bytes (e.g. a generated report) to Contabo S3 and returns the S3 key.
     * Use this for in-memory content that isn't a {@link MultipartFile}.
     */
    public String uploadBytes(String key, byte[] data, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
        evictPresignedUrl(key);
        return key;
    }

    /**
     * Reads an object's raw bytes. Used server-side when the bytes themselves are needed
     * rather than a URL — e.g. base64-encoding repair photos for the Claude vision call.
     * Returns null when the key is blank or the object cannot be read, so callers on a
     * best-effort path don't have to catch.
     */
    public byte[] downloadBytes(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key.trim())
                    .build()).asByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Best-effort content type for an object key, derived from its extension. */
    public static String contentTypeForKey(String key) {
        String lower = key == null ? "" : key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    /**
     * Presigned URL that forces a browser download (Content-Disposition: attachment)
     * with the given file name. Use for generated files like revenue exports.
     */
    public String getPresignedDownloadUrl(String key, String fileName) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String safeName = (fileName == null ? "download" : fileName).replace("\"", "").replace("\n", "").replace("\r", "");
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .responseContentDisposition("attachment; filename=\"" + safeName + "\"")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(2))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Generates a presigned URL for a given S3 key or existing full URL.
     */
    public String getPresignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        // If it's already a full URL, extract the path/key part
        String cleanKey = key;
        if (key.startsWith("http")) {
            // Remove the base URL part (e.g., publicUrl + bucketName) to get just the key
            String baseUrl = properties.getPublicUrl();
            if (key.contains(baseUrl)) {
                cleanKey = key.substring(key.indexOf(baseUrl) + baseUrl.length());
                if (cleanKey.startsWith("/")) {
                    cleanKey = cleanKey.substring(1);
                }
            } else if (key.contains(properties.getBucketName())) {
                // Fallback: extract everything after the bucket name
                cleanKey = key.substring(key.indexOf(properties.getBucketName()) + properties.getBucketName().length());
                if (cleanKey.startsWith("/")) {
                    cleanKey = cleanKey.substring(1);
                }
            } else {
                return key; // External URL, return as is
            }
        }

        // Strip any existing query string — the presigner adds its own
        int queryIdx = cleanKey.indexOf('?');
        if (queryIdx != -1) {
            cleanKey = cleanKey.substring(0, queryIdx);
        }

        // Public catalogue and editorial imagery gets the long-lived, cacheable URL. Private
        // assets (avatars, refund evidence, licences, support attachments) do not: they are seen
        // by one person, so a stable URL buys nothing, and a leaked one would stay valid.
        if (!isPubliclyCacheable(cleanKey)) {
            return presignGet(cleanKey, null, Duration.ofHours(2));
        }

        final String productKey = cleanKey;
        final Instant now = Instant.now();

        // Bound memory: sweep expired entries once the cache grows past the cap.
        if (presignedUrlCache.size() > PRESIGN_CACHE_MAX_ENTRIES) {
            presignedUrlCache.values().removeIf(c -> c.expiresAt().isBefore(now));
        }

        // The in-memory copy is the fast path — a product list asks for fifty of these, and none
        // of them should cost a Redis round trip once this host has seen the key.
        CachedUrl local = presignedUrlCache.get(productKey);
        if (local != null && local.expiresAt().isAfter(now)) {
            return local.url();
        }

        // Deliberately NOT inside compute(): that holds a bin lock, and this does network I/O.
        // Two threads racing the same key is harmless — Redis settles which url they both use.
        CachedUrl resolved = sharedPresign(productKey, now);
        presignedUrlCache.put(productKey, resolved);
        return resolved.url();
    }

    /**
     * The url for a product key, agreed across hosts through Redis.
     *
     * <p>Its local expiry tracks what Redis says is LEFT of the shared entry, not a fresh window —
     * caching a url for four more hours when the shared copy expires in ten minutes would leave
     * this host serving a signature the others have already rotated away from, and eventually one
     * that has expired outright.
     *
     * <p>If Redis cannot be reached the image still loads: we sign locally and carry on. A cache
     * being down is a performance problem, and must never become a broken-photo problem.
     */
    private CachedUrl sharedPresign(String productKey, Instant now) {
        String redisKey = PRESIGN_REDIS_PREFIX + productKey;
        try {
            String shared = redis.opsForValue().get(redisKey);
            if (shared != null) {
                return new CachedUrl(shared, now.plus(remainingTtl(redisKey)));
            }

            String signed = presignGet(productKey, IMAGE_CACHE_CONTROL, PRESIGN_SIGNATURE_TTL);
            // setIfAbsent, not set: if another host signed this key a moment ago, its url is
            // already in browsers and CDN caches. Ours would be a second one for the same photo.
            Boolean won = redis.opsForValue().setIfAbsent(redisKey, signed, PRESIGN_CACHE_TTL);
            if (Boolean.FALSE.equals(won)) {
                String winner = redis.opsForValue().get(redisKey);
                if (winner != null) {
                    return new CachedUrl(winner, now.plus(remainingTtl(redisKey)));
                }
            }
            return new CachedUrl(signed, now.plus(PRESIGN_CACHE_TTL));
        } catch (RuntimeException redisUnavailable) {
            log.warn("Presign cache unreachable, signing {} locally: {}", productKey,
                    redisUnavailable.toString());
            return new CachedUrl(presignGet(productKey, IMAGE_CACHE_CONTROL, PRESIGN_SIGNATURE_TTL),
                    now.plus(PRESIGN_CACHE_TTL));
        }
    }

    private static boolean isPubliclyCacheable(String cleanKey) {
        return CACHEABLE_PREFIXES.stream().anyMatch(cleanKey::startsWith);
    }

    /**
     * Forgets the cached URL for a key, so the next read signs a fresh one.
     *
     * <p>Called whenever an object is written or removed. Without it, replacing a banner would
     * leave up to four hours of visitors looking at the image it replaced — the cost of making
     * these URLs stable, paid back at the one moment it matters.
     */
    private void evictPresignedUrl(String key) {
        if (key == null || key.isBlank()) return;
        presignedUrlCache.remove(key);
        try {
            redis.delete(PRESIGN_REDIS_PREFIX + key);
        } catch (RuntimeException redisUnavailable) {
            // The entry lapses on its own within the window; nothing here is worth failing an
            // upload over.
            log.warn("Could not evict presign cache for {}: {}", key, redisUnavailable.toString());
        }
    }

    private Duration remainingTtl(String redisKey) {
        Long seconds = redis.getExpire(redisKey, TimeUnit.SECONDS);
        return (seconds != null && seconds > 0) ? Duration.ofSeconds(seconds) : PRESIGN_UNKNOWN_TTL;
    }

    /**
     * Signs a GET URL for an object key. When {@code cacheControl} is non-null it
     * is attached as the response's Cache-Control so the browser caches the bytes.
     */
    private String presignGet(String cleanKey, String cacheControl, Duration signatureTtl) {
        GetObjectRequest.Builder getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(cleanKey);
        if (cacheControl != null) {
            getObjectRequest.responseCacheControl(cacheControl);
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(signatureTtl)
                .getObjectRequest(getObjectRequest.build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Generates a presigned PUT URL so a client (browser) can upload a file
     * directly to Contabo S3, bypassing the application server and any edge
     * body-size limits. The client MUST send the exact same Content-Type that
     * is signed here, and nothing else, or the upload will be rejected.
     */
    public String generatePresignedUploadUrl(String key, String contentType, Duration expiry) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    /**
     * Server-side copy of an object within the same bucket (no data is streamed
     * through this application). Used to move a staged upload into its final key.
     */
    public void copyObject(String sourceKey, String destinationKey) {
        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(properties.getBucketName())
                .sourceKey(sourceKey)
                .destinationBucket(properties.getBucketName())
                .destinationKey(destinationKey)
                .build();

        s3Client.copyObject(copyRequest);
    }

    /**
     * Deletes a single file from Contabo S3.
     */
    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
        evictPresignedUrl(key);
    }

    /**
     * Deletes all objects with a given prefix (simulating folder deletion).
     * Prefix format: products/{uuid}/
     */
    public void deleteFolder(String prefix) {
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(properties.getBucketName())
                .prefix(prefix)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        if (!listResponse.contents().isEmpty()) {
            List<ObjectIdentifier> identifiers = listResponse.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .collect(Collectors.toList());

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(properties.getBucketName())
                    .delete(Delete.builder().objects(identifiers).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
            identifiers.forEach(id -> evictPresignedUrl(id.key()));
        }
    }
}
