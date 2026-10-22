package dev.requestportal.request.template;

public enum FieldType {
    TEXT,
    TEXTAREA,
    /** One of the field's options. */
    SELECT,
    /** One or more file ids from document-service. */
    FILE,
    IMAGE,
    /** An academic term code from org-service, e.g. 2026-2027-1. */
    TERM,
    /** A school year from org-service, e.g. 2026-2027. */
    SCHOOL_YEAR;

    public boolean holdsFiles() {
        return this == FILE || this == IMAGE;
    }
}
