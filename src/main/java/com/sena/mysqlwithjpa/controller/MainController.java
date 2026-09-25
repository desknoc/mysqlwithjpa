package com.sena.mysqlwithjpa.controller;

import com.sena.mysqlwithjpa.dto.UserRequest;
import com.sena.mysqlwithjpa.dto.UserResponse;
import com.sena.mysqlwithjpa.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User resource, frozen route table (rest-api-redesign design Decision 1).
 * The legacy server-side {@code /demo/**} endpoints are gone — they return 404.
 * All error conditions flow through {@link ExceptionController}'s ApiError
 * envelope; passwords are write-only inbound and never leave the service.
 */
@RestController
@RequestMapping("/api/users")
public class MainController {

    private final UserService userService;

    public MainController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Validated(UserRequest.OnCreate.class) @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Integer id) {
        return userService.findById(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Integer id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        userService.delete(id);
    }
}
