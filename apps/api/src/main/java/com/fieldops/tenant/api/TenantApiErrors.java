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

@RestControllerAdvice
public class TenantApiErrors {
    private final com.fieldops.audit.SecurityAudit audit;
    public TenantApiErrors(com.fieldops.audit.SecurityAudit audit) { this.audit = audit; }
    private void rejected() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        java.util.UUID actor = null;
        if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            actor = java.util.UUID.fromString(auth.getName());
        }
        audit.independent(actor, null, "REQUEST_REJECTED", "FAILURE", null);
    }

    @ExceptionHandler(TenantAccessException.class)
    ResponseEntity<ApiError> access(TenantAccessException exception) {
        rejected();
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    ResponseEntity<ApiError> invalidInput(Exception exception) {
        rejected();
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "The request contains invalid fields."));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> conflict(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(new ApiError("CONCURRENT_UPDATE", "The record was changed by another request."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(Exception exception) {
        return ResponseEntity.status(409).body(new ApiError("CONFLICT", "This operation conflicts with an existing record. Use sign in for an existing account, or choose a different business slug."));
    }

    public record ApiError(String code, String message) { }

}
