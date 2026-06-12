package com.locallegalrag.controller;

import com.locallegalrag.service.BadRequestException;
import com.locallegalrag.service.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException exc) {
        return error(HttpStatus.NOT_FOUND, exc.getMessage());
    }

    @ExceptionHandler({
            BadRequestException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MaxUploadSizeExceededException.class
    })
    ResponseEntity<Map<String, Object>> badRequest(Exception exc) {
        return error(HttpStatus.BAD_REQUEST, exc.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Map<String, Object>> runtime(RuntimeException exc) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exc.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message == null ? status.getReasonPhrase() : message
        ));
    }
}
