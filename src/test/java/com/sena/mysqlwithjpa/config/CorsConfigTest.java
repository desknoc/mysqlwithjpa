package com.sena.mysqlwithjpa.config;

import com.sena.mysqlwithjpa.controller.MainController;
import com.sena.mysqlwithjpa.dto.UserResponse;
import com.sena.mysqlwithjpa.entity.Rol;
import com.sena.mysqlwithjpa.entity.TipoApoyo;
import com.sena.mysqlwithjpa.entity.TipoDocumento;
import com.sena.mysqlwithjpa.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Threat-matrix CORS coverage: only {@code http://localhost:3000} may receive
 * CORS grants. Foreign origins get NO allow-origin header and NO wildcard
 * (and the rejection must not 500); server-to-server requests without an
 * Origin header proceed untouched. Runs as a web slice — no Docker needed.
 */
@WebMvcTest(MainController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class CorsConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String FOREIGN_ORIGIN = "http://evil.example.com";
    private static final String PROBE_URL = "/api/users/7";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void stubService() {
        when(userService.findById(7)).thenReturn(new UserResponse(
                7, "Ana", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L, "3001234567", null,
                "ana@example.com", Rol.USUARIO, TipoApoyo.regular,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0)));
    }

    @Test
    void preflightFromTheAllowedOriginIsGranted() throws Exception {
        mockMvc.perform(options(PROBE_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void actualRequestFromTheAllowedOriginCarriesTheGrant() throws Exception {
        mockMvc.perform(get(PROBE_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void foreignOriginReceivesNoAllowOriginAndNoWildcard() throws Exception {
        mockMvc.perform(options(PROBE_URL)
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden()) // rejected preflight — explicitly not a 500
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(get(PROBE_URL)
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requestWithoutOriginProceedsNormally() throws Exception {
        mockMvc.perform(get(PROBE_URL))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
