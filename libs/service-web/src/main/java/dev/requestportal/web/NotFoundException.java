package dev.requestportal.web;

/** The resource does not exist, or the caller is not allowed to know that it exists. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
