package org.egov.filestore.domain.exception;

public class InvalidFileUploadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidFileUploadException(String message) {
        super(message);
    }

    public InvalidFileUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
