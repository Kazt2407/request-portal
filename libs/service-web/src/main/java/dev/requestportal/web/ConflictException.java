package dev.requestportal.web;

/** The request clashes with the current state, e.g. a duplicate code, a stale version or a finished step. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
