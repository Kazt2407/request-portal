package dev.requestportal.request.file;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Uploaded files, owned by document-service. */
public interface FileDirectory {

    /** @param problem why the signed-in user cannot use the file, or {@code null} if they can */
    record FileCheck(UUID id, String fileName, String contentType, String problem) {

        public boolean isImage() {
            return contentType != null && contentType.startsWith("image/");
        }

        /** The file's name for messages, or its id if document-service does not know it. */
        public String label() {
            return fileName != null ? fileName : id.toString();
        }
    }

    /** Whether the signed-in user may put these files into a submission or a completed step. */
    Map<UUID, FileCheck> check(Collection<UUID> fileIds);
}
