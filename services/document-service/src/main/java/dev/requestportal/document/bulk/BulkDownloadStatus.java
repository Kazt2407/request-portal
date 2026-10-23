package dev.requestportal.document.bulk;

public enum BulkDownloadStatus {
    PENDING,
    RUNNING,
    READY,
    FAILED,
    /** The archive was deleted after its time to live. */
    EXPIRED
}
