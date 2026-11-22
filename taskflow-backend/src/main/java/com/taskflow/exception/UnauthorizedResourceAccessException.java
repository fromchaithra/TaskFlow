package com.taskflow.exception;

public class UnauthorizedResourceAccessException extends RuntimeException {

    public UnauthorizedResourceAccessException(String message) {
        super(message);
    }
}
