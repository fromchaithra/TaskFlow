package com.taskflow.exception;

public class InvalidWorkflowTransitionException extends RuntimeException {

    public InvalidWorkflowTransitionException(String message) {
        super(message);
    }
}
