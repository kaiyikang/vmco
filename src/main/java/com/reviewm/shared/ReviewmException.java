package com.reviewm.shared;

public class ReviewmException extends RuntimeException {
    public ReviewmException(String message) {
        super(message);
    }

    public ReviewmException(String message, Throwable cause) {
        super(message, cause);
    }
}
