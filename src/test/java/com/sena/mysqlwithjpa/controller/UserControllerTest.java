package com.sena.mysqlwithjpa.controller;

import com.sena.mysqlwithjpa.config.CorsConfig;
import com.sena.mysqlwithjpa.config.SecurityConfig;
import com.sena.mysqlwithjpa.dto.UserResponse;
import com.sena.mysqlwithjpa.entity.Rol;
import com.sena.mysqlwithjpa.entity.TipoApoyo;
import com.sena.mysqlwithjpa.entity.TipoDocumento;
import com.sena.mysqlwithjpa.exception.RateLimitExceededException;
import com.sena.mysqlwithjpa.service.UserService;
import com.sena.mysqlwithjpa.service.exception.DuplicateResourceException;
import com.sena.mysqlwithjpa.service.exception.NotFoundException;
import com.sena.mysqlwithjpa.util.SanitizationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice contract for the frozen route table (design Decision 1) on
 * {@code /api/users}: status mappings 201/200/204/400/404/409/429/500 through
 * the ApiError envelope, password never serialized, and the threat-matrix
 * routing regression — legacy {@code /demo/**} and {@code /login} MUST 404.
 * {@link UserService} is mocked; no Docker, no database.
 */
@WebMvcTest(MainController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final String VALID_CREATE_JSON = """
            {
              "primerNombre": "Ana",
              "primerApellido": "Garcia",
              "tipoDocumento": "CC",
              "documento": 1234567890,
              "celular": "3001234567",
              "correoElectronico": "ana@example.com",
              "contrasena": "sup3r-segura"
            }
            """;

    private static final String FULL_UPDATE_JSON = """
            {
              "primerNombre": "Ana",
              "primerApellido": "Garcia",
              "tipoDocumento": "CC",
              "documento": 1234567890,
              "celular": "3119998877",
              "correoElectronico": "ana@example.com"
            }
            """;

    private static UserResponse responseOfAna() {
        return new UserResponse(
                7, "Ana", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L, "3001234567", null,
                "ana@example.com", Rol.USUARIO, TipoApoyo.regular,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    // --- POST /api/users ---

    @Test
    void createReturns201WithoutAnyPasswordMaterial() throws Exception {
        when(userService.create(any())).thenReturn(responseOfAna());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.correoElectronico").value("ana@example.com"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(content().string(not(containsString("$2"))));
    }

    @Test
    void createMissingPrimerNombreReturns400EnvelopeAndPersistsNothing() throws Exception {
        String missing = """
                {
                  "primerApellido": "Garcia",
                  "tipoDocumento": "CC",
                  "documento": 1234567890,
                  "celular": "3001234567",
                  "correoElectronico": "ana@example.com",
                  "contrasena": "sup3r-segura"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missing))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/users"));
        verifyNoInteractions(userService);
    }

    @Test
    void createWithDuplicateDocumentoReturns409NamingTheField() throws Exception {
        when(userService.create(any()))
                .thenThrow(new DuplicateResourceException("documento", 1234567890L));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("documento")));
    }

    @Test
    void createWithShortPasswordReturns400WithoutHashing() throws Exception {
        when(userService.create(any()))
                .thenThrow(new IllegalArgumentException("contrasena must be at least 8 characters"));

        String shortPassword = VALID_CREATE_JSON.replace("sup3r-segura", "abc123");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createWithScriptPayloadReturns400() throws Exception {
        when(userService.create(any()))
                .thenThrow(new SanitizationException("primerNombre", "rejected content"));

        String xss = VALID_CREATE_JSON.replace("\"Ana\"", "\"<script>alert(1)</script>\"");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(xss))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- GET /api/users/{id} ---

    @Test
    void getByIdReturns200WithoutPassword() throws Exception {
        when(userService.findById(7)).thenReturn(responseOfAna());

        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.primerNombre").value("Ana"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    void getByIdReturns404EnvelopeForMissingUser() throws Exception {
        when(userService.findById(99)).thenThrow(new NotFoundException("User", 99));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/users/99"));
    }

    // --- PUT /api/users/{id} ---

    @Test
    void updateWithoutContrasenaIsValidAndReturns200() throws Exception {
        when(userService.update(eq(7), any())).thenReturn(responseOfAna());

        mockMvc.perform(put("/api/users/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    void updateOfMissingUserReturns404() throws Exception {
        when(userService.update(eq(99), any())).thenThrow(new NotFoundException("User", 99));

        mockMvc.perform(put("/api/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_UPDATE_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- DELETE /api/users/{id} ---

    @Test
    void deleteExistingReturns204WithEmptyBody() throws Exception {
        mockMvc.perform(delete("/api/users/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(userService).delete(7);
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        doThrow(new NotFoundException("User", 99)).when(userService).delete(99);

        mockMvc.perform(delete("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- Global error envelope ---

    @Test
    void rateLimitSignals429WithRetryAfterHeader() throws Exception {
        when(userService.findById(7)).thenThrow(new RateLimitExceededException(60));

        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void unhandledFailureReturnsSanitized500Envelope() throws Exception {
        when(userService.findById(7))
                .thenThrow(new RuntimeException("SQLSTATE[45000]: contrasena too short at Table usuario"));

        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Error Interno del Servidor"))
                .andExpect(content().string(not(containsString("SQLSTATE"))));
    }

    // --- Task 4.6: threat-matrix routing regression (legacy routes MUST 404) ---

    @Test
    void legacyDemoAddReturns404() throws Exception {
        mockMvc.perform(post("/demo/add")
                        .param("name", "Ana")
                        .param("email", "ana@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyDemoAllReturns404() throws Exception {
        mockMvc.perform(get("/demo/all"))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginPathReturns404() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":\"x\",\"pass\":\"y\"}"))
                .andExpect(status().isNotFound());
    }
}
