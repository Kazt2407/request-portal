package dev.requestportal.web;

import java.util.Map;
import java.util.TreeMap;

/** An all-or-nothing bulk action failed for some items; nothing was changed. */
public class BulkOperationException extends RuntimeException {

    private final Map<String, String> failures;

    /** @param failures reason per item, keyed by an identifier the client recognises */
    public BulkOperationException(Map<String, String> failures) {
        super("Bulk action failed for " + failures.keySet());
        this.failures = new TreeMap<>(failures);
    }

    public Map<String, String> getFailures() {
        return failures;
    }
}
