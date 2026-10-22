package dev.requestportal.web;

/**
 * A service or system this request depends on failed or could not be reached. Answered with 502.
 *
 * @see #getDetail() the client-safe message; the exception message and cause are only logged
 */
public class UpstreamServiceException extends RuntimeException {

    private final String detail;

    public UpstreamServiceException(String message, String detail, Throwable cause) {
        super(message, cause);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
