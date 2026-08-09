package com.bloodbank.bloodbank.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resource) {
        super("NOT_FOUND", resource + " not found", HttpStatus.NOT_FOUND);
    }
}
