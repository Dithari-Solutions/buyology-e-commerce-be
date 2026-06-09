package com.buyology.ecommerce.infrastructure.external;

import com.buyology.ecommerce.infrastructure.config.ContaboProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContaboObjectService {

    // Object-key prefixes whose images we brand with the Buyology watermark.
    // ONLY product catalog images are watermarked. Story, banner, news (marketing/
    // editorial) and users/ (avatars), refunds/ (evidence), documents (licenses)
    // must NOT be altered.
    private static final List<String> WATERMARK_PREFIXES = List.of("products/");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ContaboProperties properties;
    private final WatermarkService watermarkService;

    public ContaboObjectService(S3Client s3Client, S3Presigner s3Presigner,
                                ContaboProperties properties, WatermarkService watermarkService) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.watermarkService = watermarkService;
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
        return key;
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

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(cleanKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(2)) // URL valid for 2 hours
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
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
        }
    }
}
