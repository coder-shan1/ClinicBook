package com.clinicbook.common.error;

import org.springframework.http.HttpStatus;

/**
 * Business or protocol failure that maps directly onto the standard error response.
 * The {@code code} is a stable, machine-readable identifier for clients.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
