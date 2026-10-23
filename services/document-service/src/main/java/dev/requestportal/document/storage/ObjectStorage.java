package dev.requestportal.document.storage;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** The bucket: pre-signed URLs for clients, metadata checks and deletes for this service. */
@Component
public class ObjectStorage implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    public record PresignedUrl(String url, Instant expiresAt) {
    }

    public record ObjectInfo(long size, String contentType) {
    }

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    /** A URL the client can PUT the file to, with the given Content-Type header (it is part of the signature). */
    public PresignedUrl presignUpload(String key, String contentType) {
        var request = PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).build();
        var presigned = presigner.presignPutObject(r -> r.signatureDuration(properties.urlTtl()).putObjectRequest(request));
        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    /** A URL that downloads the object under its original name (RFC 6266, so Vietnamese names survive). */
    public PresignedUrl presignDownload(String key, String fileName) {
        var disposition = ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build();
        var request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .responseContentDisposition(disposition.toString())
                .build();
        var presigned = presigner.presignGetObject(r -> r.signatureDuration(properties.urlTtl()).getObjectRequest(request));
        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    public Optional<ObjectInfo> head(String key) {
        try {
            HeadObjectResponse head = s3.headObject(r -> r.bucket(properties.bucket()).key(key));
            return Optional.of(new ObjectInfo(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /** The object's content as a stream; the caller closes it. */
    public InputStream open(String key) {
        return s3.getObject(r -> r.bucket(properties.bucket()).key(key));
    }

    /** A stream that uploads whatever is written to it as a new object, in parts. */
    public MultipartUploadStream create(String key, String contentType) {
        return new MultipartUploadStream(s3, properties.bucket(), key, contentType);
    }

    public byte[] read(String key) {
        return s3.getObjectAsBytes(r -> r.bucket(properties.bucket()).key(key)).asByteArray();
    }

    public void delete(String key) {
        s3.deleteObject(r -> r.bucket(properties.bucket()).key(key));
    }

    /** Creates the bucket on startup when configured to (local development); in AWS Terraform owns it. */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.createBucket()) {
            return;
        }
        try {
            s3.headBucket(r -> r.bucket(properties.bucket()));
        } catch (NoSuchBucketException exception) {
            s3.createBucket(r -> r.bucket(properties.bucket()));
            log.info("Created bucket {}", properties.bucket());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            s3.createBucket(r -> r.bucket(properties.bucket()));
            log.info("Created bucket {}", properties.bucket());
        }
    }
}
