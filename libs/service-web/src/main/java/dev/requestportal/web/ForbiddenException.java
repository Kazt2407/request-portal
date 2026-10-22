package dev.requestportal.web;

/** The caller may see the resource but not perform this action on it, e.g. a step of another department. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
