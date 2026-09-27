package com.fieldops.tenant.api;

import com.fieldops.tenant.application.TenantAccessException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = TenantWorkspaceController.class)
public class TenantApiErrors {
    @ExceptionHandler(TenantAccessException.class)
    ResponseEntity<ApiError> access(TenantAccessException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    ResponseEntity<ApiError> invalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "The request contains invalid fields."));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> conflict(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(new ApiError("CONCURRENT_UPDATE", "The record was changed by another request."));
    }

    public record ApiError(String code, String message) { }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException exception) {
        // No email directory or database constraint details in a public signup response.
        return ResponseEntity.status(409).header("Cache-Control", "no-store")
                .body(new ApiError("ONBOARDING_CONFLICT", "Unable to create this business with those details. Try another business identifier or sign in if you already have an account."));
    }
}
