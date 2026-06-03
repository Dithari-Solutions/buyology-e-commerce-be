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

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ContaboProperties properties;

    public ContaboObjectService(S3Client s3Client, S3Presigner s3Presigner, ContaboProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /**
     * Uploads a file to Contabo S3 and returns the S3 key.
     * Path format: products/{uuid}/{filename}
     */
    public String uploadFile(String key, MultipartFile file) {
        try {
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
