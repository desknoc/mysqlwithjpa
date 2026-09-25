package com.sena.mysqlwithjpa.config;

import com.sena.mysqlwithjpa.controller.MainController;
import com.sena.mysqlwithjpa.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void stubRepository() {
        when(userRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void preflightFromTheAllowedOriginIsGranted() throws Exception {
        mockMvc.perform(options("/demo/all")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void actualRequestFromTheAllowedOriginCarriesTheGrant() throws Exception {
        mockMvc.perform(get("/demo/all")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void foreignOriginReceivesNoAllowOriginAndNoWildcard() throws Exception {
        mockMvc.perform(options("/demo/all")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden()) // rejected preflight — explicitly not a 500
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(get("/demo/all")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requestWithoutOriginProceedsNormally() throws Exception {
        mockMvc.perform(get("/demo/all"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
