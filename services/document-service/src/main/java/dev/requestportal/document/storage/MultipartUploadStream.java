package dev.requestportal.document.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;

/**
 * Writes an object of unknown size to S3 as a multipart upload, holding at most one part in memory. Call
 * {@link #complete()} when done; closing without completing aborts the upload so no parts are left behind.
 */
public final class MultipartUploadStream extends OutputStream {

    /** S3's minimum part size is 5 MiB (except the last part). */
    static final int PART_SIZE = 8 * 1024 * 1024;

    private final S3Client s3;
    private final String bucket;
    private final String key;
    private final String uploadId;
    private final List<CompletedPart> parts = new ArrayList<>();
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream(PART_SIZE);
    private long size;
    private boolean finished;

    MultipartUploadStream(S3Client s3, String bucket, String key, String contentType) {
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
        this.uploadId = s3.createMultipartUpload(r -> r.bucket(bucket).key(key).contentType(contentType)).uploadId();
    }

    @Override
    public void write(int b) {
        buffer.write(b);
        size++;
        flushPartIfFull();
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        int written = 0;
        while (written < length) {
            int chunk = Math.min(length - written, PART_SIZE - buffer.size());
            buffer.write(bytes, offset + written, chunk);
            written += chunk;
            size += chunk;
            flushPartIfFull();
        }
    }

    /** Uploads the last part and makes the object visible; returns its size in bytes. */
    public long complete() {
        uploadPart();
        s3.completeMultipartUpload(r -> r.bucket(bucket).key(key).uploadId(uploadId)
                .multipartUpload(m -> m.parts(parts)));
        finished = true;
        return size;
    }

    @Override
    public void close() throws IOException {
        if (!finished) {
            finished = true;
            s3.abortMultipartUpload(r -> r.bucket(bucket).key(key).uploadId(uploadId));
        }
    }

    private void flushPartIfFull() {
        if (buffer.size() >= PART_SIZE) {
            uploadPart();
        }
    }

    private void uploadPart() {
        if (buffer.size() == 0 && !parts.isEmpty()) {
            return;
        }
        int partNumber = parts.size() + 1;
        byte[] bytes = buffer.toByteArray();
        String etag = s3.uploadPart(r -> r.bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber),
                RequestBody.fromBytes(bytes)).eTag();
        parts.add(CompletedPart.builder().partNumber(partNumber).eTag(etag).build());
        buffer = new ByteArrayOutputStream(PART_SIZE);
    }
}
