package dev.requestportal.web;

import java.util.Map;
import java.util.TreeMap;

/** Input that bean validation cannot check, such as values of a template's dynamic fields. */
public class InvalidFieldsException extends RuntimeException {

    private final Map<String, String> errors;

    public InvalidFieldsException(Map<String, String> errors) {
        super("Some fields are invalid: " + errors.keySet());
        this.errors = new TreeMap<>(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
