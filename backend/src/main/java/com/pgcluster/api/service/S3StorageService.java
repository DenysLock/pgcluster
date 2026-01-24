package com.pgcluster.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for interacting with S3-compatible object storage.
 * Provides methods for file upload, download, presigned URL generation,
 * and bucket operations. Supports Hetzner Object Storage and other
 * S3-compatible endpoints.
 */
@Slf4j
@Service
public class S3StorageService {

    @Value("${s3.endpoint:}")
    private String endpoint;

    @Value("${s3.access-key:}")
    private String accessKey;

    @Value("${s3.secret-key:}")
    private String secretKey;

    @Value("${s3.bucket:pgcluster-backups}")
    private String bucket;

    @Value("${s3.region:eu-central-1}")
    private String region;

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private boolean configured = false;

    @PostConstruct
    public void init() {
        if (endpoint == null || endpoint.isBlank() ||
            accessKey == null || accessKey.isBlank() ||
            secretKey == null || secretKey.isBlank()) {
            log.warn("S3 storage not configured - backup functionality disabled");
            return;
        }

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of(region))
                    .forcePathStyle(true)
                    .build();

            this.s3Presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of(region))
                    .build();

            this.configured = true;
            log.info("S3 storage service initialized with endpoint: {}", endpoint);
        } catch (Exception e) {
            log.error("Failed to initialize S3 client: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (s3Presigner != null) {
            try {
                s3Presigner.close();
                log.debug("S3 presigner closed");
            } catch (Exception e) {
                log.warn("Error closing S3 presigner: {}", e.getMessage());
            }
        }
        if (s3Client != null) {
            try {
                s3Client.close();
                log.debug("S3 client closed");
            } catch (Exception e) {
                log.warn("Error closing S3 client: {}", e.getMessage());
            }
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public String getBucket() {
        return bucket;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void uploadFile(String key, InputStream inputStream, long contentLength) {
        checkConfigured();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            log.debug("Uploaded file to S3: {}", key);
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", key, e);
            throw new RuntimeException("Failed to upload file to S3: " + key, e);
        }
    }

    public void uploadString(String key, String content) {
        checkConfigured();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(request, RequestBody.fromString(content));
            log.debug("Uploaded string to S3: {}", key);
        } catch (Exception e) {
            log.error("Failed to upload string to S3: {}", key, e);
            throw new RuntimeException("Failed to upload string to S3: " + key, e);
        }
    }

    public void uploadFile(String key, byte[] data) {
        checkConfigured();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/octet-stream")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(data));
            log.debug("Uploaded {} bytes to S3: {}", data.length, key);
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", key, e);
            throw new RuntimeException("Failed to upload file to S3: " + key, e);
        }
    }

    public InputStream downloadFile(String key) {
        checkConfigured();
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            log.warn("File not found in S3: {}", key);
            return null;
        } catch (Exception e) {
            log.error("Failed to download file from S3: {}", key, e);
            throw new RuntimeException("Failed to download file from S3: " + key, e);
        }
    }

    public String downloadString(String key) {
        checkConfigured();
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            return s3Client.getObjectAsBytes(request).asUtf8String();
        } catch (NoSuchKeyException e) {
            log.warn("File not found in S3: {}", key);
            return null;
        } catch (Exception e) {
            log.error("Failed to download string from S3: {}", key, e);
            throw new RuntimeException("Failed to download string from S3: " + key, e);
        }
    }

    public void deleteFile(String key) {
        checkConfigured();
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.debug("Deleted file from S3: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", key, e);
            throw new RuntimeException("Failed to delete file from S3: " + key, e);
        }
    }

    public void deleteDirectory(String prefix) {
        checkConfigured();
        try {
            List<String> keys = listFiles(prefix);
            if (keys.isEmpty()) {
                log.debug("No files to delete in S3 directory: {}", prefix);
                return;
            }

            // Use batch delete (up to 1000 objects per request)
            int batchSize = 1000;
            for (int i = 0; i < keys.size(); i += batchSize) {
                List<String> batch = keys.subList(i, Math.min(i + batchSize, keys.size()));
                List<ObjectIdentifier> objectIds = batch.stream()
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .collect(Collectors.toList());

                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(objectIds).build())
                        .build();

                s3Client.deleteObjects(deleteRequest);
                log.debug("Batch deleted {} files from S3", batch.size());
            }
            log.info("Deleted {} files from S3 directory: {}", keys.size(), prefix);
        } catch (Exception e) {
            log.error("Failed to delete directory from S3: {}", prefix, e);
            throw new RuntimeException("Failed to delete directory from S3: " + prefix, e);
        }
    }

    public List<String> listFiles(String prefix) {
        checkConfigured();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list files from S3: {}", prefix, e);
            throw new RuntimeException("Failed to list files from S3: " + prefix, e);
        }
    }

    public long getDirectorySize(String prefix) {
        checkConfigured();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream()
                    .mapToLong(S3Object::size)
                    .sum();
        } catch (Exception e) {
            log.error("Failed to get directory size from S3: {}", prefix, e);
            return 0;
        }
    }

    public String generatePresignedUrl(String key, Duration expiration) {
        checkConfigured();
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", key, e);
            throw new RuntimeException("Failed to generate presigned URL: " + key, e);
        }
    }

    public String generatePresignedUrl(String key, int hours) {
        return generatePresignedUrl(key, Duration.ofHours(hours));
    }

    /**
     * Generate a presigned URL for uploading (PUT) a file.
     * Used for direct uploads from remote servers to S3.
     */
    public String generatePresignedPutUrl(String key, Duration expiration) {
        checkConfigured();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .putObjectRequest(putObjectRequest)
                    .build();

            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned PUT URL for: {}", key, e);
            throw new RuntimeException("Failed to generate presigned PUT URL: " + key, e);
        }
    }

    public String generatePresignedPutUrl(String key, int minutes) {
        return generatePresignedPutUrl(key, Duration.ofMinutes(minutes));
    }

    public boolean fileExists(String key) {
        checkConfigured();
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Failed to check file existence in S3: {}", key, e);
            return false;
        }
    }

    /**
     * Get the size of a file in S3.
     *
     * @param key The S3 key
     * @return File size in bytes, or -1 if file doesn't exist
     */
    public long getFileSize(String key) {
        checkConfigured();
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentLength();
        } catch (NoSuchKeyException e) {
            return -1;
        } catch (Exception e) {
            log.error("Failed to get file size from S3: {}", key, e);
            return -1;
        }
    }

    private void checkConfigured() {
        if (!configured) {
            throw new IllegalStateException("S3 storage is not configured. Please set S3_ENDPOINT, S3_ACCESS_KEY, and S3_SECRET_KEY environment variables.");
        }
    }
}
