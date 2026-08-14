package com.sena.mysqlwithjpa.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionController {

    @ExceptionHandler(MissingServletRequestParameterException.class)

    public ResponseEntity<ApiError> manejarParametroFaltante(MissingServletRequestParameterException ex)
}
