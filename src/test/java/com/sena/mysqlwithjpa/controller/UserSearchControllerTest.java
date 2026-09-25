package com.sena.mysqlwithjpa.controller;

import com.sena.mysqlwithjpa.config.CorsConfig;
import com.sena.mysqlwithjpa.config.SecurityConfig;
import com.sena.mysqlwithjpa.dto.UserResponse;
import com.sena.mysqlwithjpa.entity.Rol;
import com.sena.mysqlwithjpa.entity.TipoApoyo;
import com.sena.mysqlwithjpa.entity.TipoDocumento;
import com.sena.mysqlwithjpa.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice contract for the read-side querying behavior
 * (spec user-search-pagination): paginated listing with a HARD maximum page
 * size of 7 (oversize requests are clamped, never rejected), paginated AND
 * search on primerNombre+documento, paginated OR search where the controller
 * parses a numeric term to {@code Long} for the documento branch (non-numeric
 * or SQL-injection-shaped terms are passed through literally with a null
 * documento). Empty results are 200, never 404. {@link UserService} is
 * mocked; no Docker, no database.
 */
@WebMvcTest(MainController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class UserSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static UserResponse userNamed(String name) {
        return new UserResponse(
                7, name, null, "Garcia", null,
                TipoDocumento.CC, 1234567890L, "3001234567", null,
                "ana@example.com", Rol.USUARIO, TipoApoyo.regular,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    private static PagedModel<UserResponse> pageOf(int content, long total, int number, int size) {
        List<UserResponse> items = IntStream.range(0, content)
                .mapToObj(i -> userNamed("Ana" + i))
                .toList();
        return new PagedModel<>(new org.springframework.data.domain.PageImpl<>(
                items, org.springframework.data.domain.PageRequest.of(number, size), total));
    }

    // --- GET /api/users (paged listing) ---

    @Test
    void listingReturnsExactly7RecordsWithPageMetadataWhenMoreThan7Exist() throws Exception {
        when(userService.findAllUsers(any(Pageable.class))).thenReturn(pageOf(7, 10, 0, 7));

        mockMvc.perform(get("/api/users").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(7))
                .andExpect(jsonPath("$.page.totalElements").value(10))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(7))
                .andExpect(jsonPath("$.content[0].contrasena").doesNotExist());
    }

    @Test
    void oversizedSizeIsClampedTo7WithHttp200() throws Exception {
        when(userService.findAllUsers(any(Pageable.class))).thenReturn(pageOf(7, 10, 0, 7));

        mockMvc.perform(get("/api/users").param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(7));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).findAllUsers(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(7);
    }

    @Test
    void lastPartialPageOf10UsersHas3RecordsWithCorrectTotals() throws Exception {
        when(userService.findAllUsers(any(Pageable.class))).thenReturn(pageOf(3, 10, 1, 7));

        mockMvc.perform(get("/api/users").param("page", "1").param("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page.totalElements").value(10))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void outOfRangePageReturns200WithEmptyContentAndRealTotals() throws Exception {
        when(userService.findAllUsers(any(Pageable.class))).thenReturn(pageOf(0, 10, 7, 7));

        mockMvc.perform(get("/api/users").param("page", "99").param("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(10));
    }

    // --- GET /api/users/search/and ---

    @Test
    void andSearchPassesBothCriteriaAndReturns200() throws Exception {
        when(userService.searchAnd(eq("Ana"), eq(1234567890L), any(Pageable.class)))
                .thenReturn(pageOf(1, 1, 0, 7));

        mockMvc.perform(get("/api/users/search/and")
                        .param("primerNombre", "Ana")
                        .param("documento", "1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(userService).searchAnd(eq("Ana"), eq(1234567890L), any(Pageable.class));
    }

    @Test
    void emptyAndSearchResultIs200Not404() throws Exception {
        when(userService.searchAnd(eq("Ana"), eq(9876543210L), any(Pageable.class)))
                .thenReturn(pageOf(0, 0, 0, 7));

        mockMvc.perform(get("/api/users/search/and")
                        .param("primerNombre", "Ana")
                        .param("documento", "9876543210"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // --- GET /api/users/search/or ---

    @Test
    void orSearchPaginated12MatchesIsCappedAt7WithTotal12() throws Exception {
        when(userService.searchOr(eq("Ana"), isNull(), any(Pageable.class)))
                .thenReturn(pageOf(7, 12, 0, 7));

        mockMvc.perform(get("/api/users/search/or").param("term", "Ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(7))
                .andExpect(jsonPath("$.page.totalElements").value(12))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).searchOr(eq("Ana"), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(7);
    }

    @Test
    void numericOrTermIsParsedToLongForTheDocumentoBranch() throws Exception {
        when(userService.searchOr(eq("1122334455"), eq(1122334455L), any(Pageable.class)))
                .thenReturn(pageOf(1, 1, 0, 7));

        mockMvc.perform(get("/api/users/search/or").param("term", "1122334455"))
                .andExpect(status().isOk());

        verify(userService).searchOr(eq("1122334455"), eq(1122334455L), any(Pageable.class));
    }

    @Test
    void injectionShapedTermIsTreatedAsLiteralDataWithNullDocumentoAndNoError() throws Exception {
        when(userService.searchOr(eq("Ana' OR '1'='1"), isNull(), any(Pageable.class)))
                .thenReturn(pageOf(0, 0, 0, 7));

        mockMvc.perform(get("/api/users/search/or").param("term", "Ana' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        verify(userService).searchOr(eq("Ana' OR '1'='1"), isNull(), any(Pageable.class));
    }
}
