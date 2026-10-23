package dev.requestportal.document.event;

/** Not a readable event; retrying cannot help, so it goes straight to the dead-letter topic. */
class InvalidEventException extends RuntimeException {

    InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
