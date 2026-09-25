package com.sena.mysqlwithjpa.controller;

import com.sena.mysqlwithjpa.exception.RateLimitExceededException;
import com.sena.mysqlwithjpa.service.exception.DuplicateResourceException;
import com.sena.mysqlwithjpa.service.exception.NotFoundException;
import com.sena.mysqlwithjpa.util.SanitizationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ExceptionController {

    // Creamos la variable de clase para poder acceder a los logs
    public static final Logger log = LoggerFactory.getLogger(ExceptionController.class);

    // Excepción 400

    @ExceptionHandler(MissingServletRequestParameterException.class)

    public ResponseEntity<ApiError> manejarParametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest request){

        ApiError error = new ApiError();
        error.setTimestamp(LocalDateTime.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());

        // Agregamos los logs para los parámetros
        log.warn("Parámetro faltante en {}: {}", request.getRequestURI(), ex.getMessage());

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Excepción 400 por validación: el cliente mandó un dato que no cumple
    // las reglas declaradas con anotaciones en la entity User.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> manejarErrorValidacion(ConstraintViolationException ex, HttpServletRequest request) {

        ApiError error = new ApiError();
        error.setTimestamp(LocalDateTime.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());

        // Logs para la validación fallida
        log.warn("Validación fallida en {}: {}", request.getRequestURI(), ex.getMessage());

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Excepción 404

    @ExceptionHandler(NoSuchElementException.class)

    public ResponseEntity<ApiError> manejarElementoNoEncontrado(NoSuchElementException ex, HttpServletRequest request){

        ApiError error = new ApiError();
        error.setTimestamp(LocalDateTime.now());
        error.setStatus(HttpStatus.NOT_FOUND.value());
        error.setError(HttpStatus.NOT_FOUND.getReasonPhrase());
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());

        // Agregamos logs para los NOT_FOUND
        log.warn("Elemento no encontrado en {}: {}", request.getRequestURI(), ex.getMessage());

        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // --- rest-api-redesign Phase 4 handlers (ApiError envelope per specs) ---

    /**
     * 404 for unrouted paths (no handler / no static resource). Without this
     * handler they would fall into the generic 500 catch-all below; the spec
     * demands legacy {@code /demo/**} and {@code /login} answer 404.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoRoute(Exception ex, HttpServletRequest request) {
        log.warn("No route for {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Resource not found", request);
    }


    /** 400: bean validation on @RequestBody payloads (missing/invalid fields). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> "Invalid or missing field: " + error.getField())
                .orElse("Invalid request body");
        log.warn("Bean validation failed on {}: {}", request.getRequestURI(), message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** 400: sanitizer rejections and service-level input validation. */
    @ExceptionHandler({SanitizationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadInput(RuntimeException ex, HttpServletRequest request) {
        log.warn("Bad input on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** 404: resource addressed by id does not exist. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        log.warn("Not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /** 409: service-level uniqueness pre-check, naming the conflicting field. */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex,
                                                    HttpServletRequest request) {
        log.warn("Uniqueness conflict on {}: field={}", request.getRequestURI(), ex.getField());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * 409 fallback: a DB unique constraint wins the race against the
     * service-level pre-check. The message stays generic on purpose — raw
     * constraint text must not leak to clients.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConstraintFallback(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Uniqueness conflict", request);
    }

    /**
     * 429: MVC-side rate-limit signal (defense-in-depth; the RateLimitFilter
     * renders its own identical envelope for filter-chain rejections).
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex,
                                                    HttpServletRequest request) {
        log.warn("Rate limit exceeded on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(buildBody(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", request));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return new ResponseEntity<>(buildBody(status, message, request), status);
    }

    private static ApiError buildBody(HttpStatus status, String message, HttpServletRequest request) {
        ApiError error = new ApiError();
        error.setTimestamp(LocalDateTime.now());
        error.setStatus(status.value());
        error.setError(status.getReasonPhrase());
        error.setMessage(message);
        error.setPath(request.getRequestURI());
        return error;
    }

    // Excepción 500

    @ExceptionHandler(Exception.class)

    public ResponseEntity<ApiError> manejarErrorInesperado(Exception ex, HttpServletRequest request){

        ApiError error = new ApiError();
        error.setTimestamp(LocalDateTime.now());
        error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.setError(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        error.setMessage("Error Interno del Servidor"); // No podemos exponer mensajes de la BD al cliente, es una mala práctica
        error.setPath(request.getRequestURI());

        // Agregamos logs para el error 500
        log.error("Error inesperado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
