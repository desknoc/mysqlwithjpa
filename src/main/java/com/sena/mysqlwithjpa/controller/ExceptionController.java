package com.sena.mysqlwithjpa.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
