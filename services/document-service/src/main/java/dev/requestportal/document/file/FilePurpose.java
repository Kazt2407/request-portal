package dev.requestportal.document.file;

import java.util.Set;

/** What a file is for, which decides the allowed types and where it is stored. */
public enum FilePurpose {
    /** A value of a FILE or IMAGE field on a request. */
    REQUEST_FIELD("requests", FileTypes.DOCUMENTS_AND_IMAGES),
    /** A file staff attach when completing a step, such as a scanned certificate. */
    STEP_ATTACHMENT("steps", FileTypes.DOCUMENTS_AND_IMAGES),
    /** A .docx form template (admins only). */
    FORM_TEMPLATE("form-templates", Set.of(FileTypes.DOCX));

    private final String keyPrefix;
    private final Set<String> allowedContentTypes;

    FilePurpose(String keyPrefix, Set<String> allowedContentTypes) {
        this.keyPrefix = keyPrefix;
        this.allowedContentTypes = allowedContentTypes;
    }

    String keyPrefix() {
        return keyPrefix;
    }

    boolean allows(String contentType) {
        return allowedContentTypes.contains(contentType);
    }

    Set<String> allowedContentTypes() {
        return allowedContentTypes;
    }

    static final class FileTypes {

        static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        static final Set<String> DOCUMENTS_AND_IMAGES = Set.of(
                "application/pdf",
                "image/jpeg",
                "image/png",
                "image/webp",
                "application/msword",
                DOCX,
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private FileTypes() {
        }
    }
}
