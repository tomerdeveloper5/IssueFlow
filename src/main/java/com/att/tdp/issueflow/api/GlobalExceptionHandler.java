package com.att.tdp.issueflow.api;

import com.att.tdp.issueflow.api.dto.ApiErrorResponse;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.persistence.OptimisticLockException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI(),
                "RESOURCE_NOT_FOUND",
                "The requested resource could not be found.",
                "Verify the resource identifier and try again.",
                null
        );
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI(),
                "BAD_REQUEST",
                "The request is invalid and cannot be processed.",
                "Review the request payload and submit again.",
                null
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                safeMessage(ex.getMessage(), "The request conflicts with existing data."),
                request.getRequestURI(),
                "RESOURCE_CONFLICT",
                "The request conflicts with the current state of the resource.",
                "Refresh your data and retry the operation.",
                null
        );
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockConflict(Exception ex, HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                "Ticket was updated by another user. Please reload and try again.",
                request.getRequestURI(),
                "RESOURCE_CONFLICT",
                "The request conflicts with the current state of the resource.",
                "Refresh your data and retry the operation.",
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityConflict(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String details = flattenedExceptionMessage(ex);
        if (isDuplicateUsername(details)) {
            return error(
                    HttpStatus.CONFLICT,
                    "Username already in use",
                    request.getRequestURI(),
                    "USER_USERNAME_EXISTS",
                    "An account with this username already exists.",
                    "Choose another username and try again.",
                    null
            );
        }
        if (isDuplicateEmail(details)) {
            return error(
                    HttpStatus.CONFLICT,
                    "Email already in use",
                    request.getRequestURI(),
                    "USER_EMAIL_EXISTS",
                    "An account with this email already exists.",
                    "Use another email address and try again.",
                    null
            );
        }
        return error(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data.",
                request.getRequestURI(),
                "RESOURCE_CONFLICT",
                "The request conflicts with the current state of the resource.",
                "Review the conflicting fields and submit again.",
                null
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(Exception ex, HttpServletRequest request) {
        return error(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                request.getRequestURI(),
                "AUTH_UNAUTHORIZED",
                "Authentication failed for this request.",
                "Authenticate with valid credentials and retry.",
                null
        );
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleForbidden(Exception ex, HttpServletRequest request) {
        return error(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                request.getRequestURI(),
                "ACCESS_DENIED",
                "You are authenticated but do not have permission to access this resource.",
                "Use an account with sufficient privileges or contact an administrator.",
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                request.getRequestURI(),
                "VALIDATION_FAILED",
                "One or more request fields failed validation.",
                "Correct the field errors and retry.",
                errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                request.getRequestURI(),
                "BAD_REQUEST",
                "The request body is not valid JSON for this endpoint.",
                "Fix the JSON format and submit again.",
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                request.getRequestURI(),
                "INTERNAL_SERVER_ERROR",
                "The server failed to process the request.",
                "Retry later or contact support if the issue persists.",
                null
        );
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String message,
            String path,
            String errorCode,
            String explanation,
            String action,
            Map<String, String> validationErrors
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                errorCode,
                explanation,
                action,
                validationErrors
        ));
    }

    private String safeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    private String flattenedExceptionMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(current.getMessage().toLowerCase(Locale.ROOT));
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    private boolean isDuplicateEmail(String details) {
        return matchesUniqueField(details, "email");
    }

    private boolean isDuplicateUsername(String details) {
        return matchesUniqueField(details, "username");
    }

    private boolean matchesUniqueField(String details, String field) {
        if (!isUniqueViolation(details)) {
            return false;
        }
        return details.contains("key (" + field + ")")
                || details.contains("users(" + field + " ")
                || details.contains("users(" + field + ")")
                || details.contains("on public.users(" + field + " ")
                || details.contains("on public.users(" + field + ")")
                || details.contains("(" + field + ")=(");
    }

    private boolean isUniqueViolation(String details) {
        return details.contains("duplicate")
                || details.contains("unique")
                || details.contains("constraint");
    }
}


