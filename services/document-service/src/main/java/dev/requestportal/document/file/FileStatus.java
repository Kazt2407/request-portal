package dev.requestportal.document.file;

public enum FileStatus {
    /** An upload URL was issued; the file may not be in storage yet. */
    PENDING,
    /** The file is in storage and was checked; it can be used in a request. */
    UPLOADED,
    /** The file belongs to a request (or a form) and is kept. */
    ATTACHED
}
