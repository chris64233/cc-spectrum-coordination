package com.chris64233.spectrumcoordination.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chris64233.spectrumcoordination.service.ApiException;

/**
 * 统一错误响应：{ code, message, errors, time }。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(String code, String message, List<String> errors, Instant time) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorBody(ex.getStatus().getReasonPhrase(), ex.getMessage(), List.of(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorBody(HttpStatus.BAD_REQUEST.getReasonPhrase(), "参数校验失败", errors, Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                        List.of(), Instant.now()));
    }
}
