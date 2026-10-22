package dev.requestportal.notification.event;

/** The message is not a readable event. Retrying cannot help, so it goes straight to the dead-letter topic. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
