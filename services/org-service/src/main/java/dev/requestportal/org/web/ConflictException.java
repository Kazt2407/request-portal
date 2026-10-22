package dev.requestportal.org.web;

/** The request clashes with the current state, e.g. a duplicate code or a stale version. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
