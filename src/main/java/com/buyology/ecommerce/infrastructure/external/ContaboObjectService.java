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
