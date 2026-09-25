package com.sena.mysqlwithjpa.controller;

import com.sena.mysqlwithjpa.dto.UserRequest;
import com.sena.mysqlwithjpa.dto.UserResponse;
import com.sena.mysqlwithjpa.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Hard maximum page size (spec user-search-pagination, design Decision 5). */
    static final int MAX_PAGE_SIZE = 7;

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

    /**
     * Read-side querying (design Decision 5): oversize {@code size} is silently
     * clamped to {@link #MAX_PAGE_SIZE}, never rejected. The OR {@code term} is
     * parsed to {@code Long} for the documento branch ONLY when numeric; any
     * other text (including SQL-injection-shaped input) is forwarded literally
     * as string criteria with a {@code null} documento — derived queries keep
     * every value a bound parameter.
     */
    @GetMapping
    public PagedModel<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        return userService.findAllUsers(pageableOf(page, size));
    }

    @GetMapping("/search/and")
    public PagedModel<UserResponse> searchAnd(
            @RequestParam String primerNombre,
            @RequestParam Long documento,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        return userService.searchAnd(primerNombre, documento, pageableOf(page, size));
    }

    @GetMapping("/search/or")
    public PagedModel<UserResponse> searchOr(
            @RequestParam String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        return userService.searchOr(term, parseNumericTerm(term), pageableOf(page, size));
    }

    private static Pageable pageableOf(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private static Long parseNumericTerm(String term) {
        try {
            return Long.parseLong(term.trim());
        } catch (NumberFormatException notNumeric) {
            return null;
        }
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
