package com.chatbot.chatbot_backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
public class ExceptionHandlerClass {

    @ExceptionHandler(value = IllegalArgumentException.class)
    protected ResponseEntity<Error> illegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse("Invalid argument", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = IllegalStateException.class)
    protected ResponseEntity<Error> illegalState(IllegalStateException ex) {
        return buildErrorResponse("Invalid state", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    protected ResponseEntity<Error> validationException(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildErrorResponse("Validation error", details, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = RuntimeException.class)
    protected ResponseEntity<Error> runtimeException(RuntimeException ex) {
        return buildErrorResponse("Runtime error", ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(value = Exception.class)
    protected ResponseEntity<Error> genericException(Exception ex) {
        return buildErrorResponse("Internal server error", ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Error> buildErrorResponse(String message, String details, HttpStatus status) {
        Error error = new Error(message, details, status.value());
        return new ResponseEntity<>(error, status);
    }
}