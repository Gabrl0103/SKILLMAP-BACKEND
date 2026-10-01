package com.skillmap.api.domain.exception;

public class InvalidJobDescriptionException extends RuntimeException {
    public InvalidJobDescriptionException(String message) {
        super(message);
    }
}
