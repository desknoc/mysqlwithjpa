package com.sena.mysqlwithjpa.service;

import com.sena.mysqlwithjpa.dto.UserRequest;
import com.sena.mysqlwithjpa.dto.UserResponse;
import com.sena.mysqlwithjpa.entity.Rol;
import com.sena.mysqlwithjpa.entity.TipoApoyo;
import com.sena.mysqlwithjpa.entity.TipoDocumento;
import com.sena.mysqlwithjpa.entity.User;
import com.sena.mysqlwithjpa.repository.UserRepository;
import com.sena.mysqlwithjpa.service.exception.DuplicateResourceException;
import com.sena.mysqlwithjpa.service.exception.NotFoundException;
import com.sena.mysqlwithjpa.service.log.LogService;
import com.sena.mysqlwithjpa.util.SanitizationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito slice for {@link UserService}: hashing before persistence, plaintext
 * length validation BEFORE the hash step, uniqueness pre-checks, sanitizer
 * rejections, and trigger-owned column hygiene. No Spring context, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String PLAINTEXT = "sup3r-segura";
    private static final String STORED_HASH = "$2b$10$abcdefghijklmnopqrstuv1234567890abcdefgh12345";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LogService logService;

    @InjectMocks
    private UserService userService;

    private static UserRequest validRequest() {
        return new UserRequest(
                "Ana", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L,
                "3001234567", null, "ana@example.com",
                PLAINTEXT, null, null);
    }

    private static User existingUser() {
        User user = new User();
        user.setId(7);
        user.setPrimerNombre("Ana");
        user.setPrimerApellido("Garcia");
        user.setTipoDocumento(TipoDocumento.CC);
        user.setDocumento(1234567890L);
        user.setCelular("3001234567");
        user.setCorreoElectronico("ana@example.com");
        user.setContrasena(STORED_HASH);
        user.setRol(Rol.USUARIO);
        user.setFechaRegistro(LocalDateTime.of(2026, 1, 1, 10, 0));
        user.setUltimaActualizacion(LocalDateTime.of(2026, 1, 1, 10, 0));
        return user;
    }

    // (a) create: plaintext of 8+ chars is hashed before save; stored != plaintext
    @Test
    void createHashesThePlaintextPasswordBeforeSaving() {
        when(passwordEncoder.encode(PLAINTEXT)).thenReturn(STORED_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.create(validRequest());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(STORED_HASH, saved.getValue().getContrasena());
        assertNotEquals(PLAINTEXT, saved.getValue().getContrasena());
        assertEquals("ana@example.com", response.correoElectronico());
        assertEquals(1234567890L, response.documento());
    }

    // (b) create: contrasena "abc123" rejected before hashing or persistence
    @Test
    void createRejectsShortPasswordBeforeHashingOrSaving() {
        UserRequest request = new UserRequest(
                "Ana", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L,
                "3001234567", null, "ana@example.com",
                "abc123", null, null);

        assertThrows(IllegalArgumentException.class, () -> userService.create(request));
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    // (c) create: duplicate documento rejected, naming the field
    @Test
    void createRejectsDuplicateDocumento() {
        when(userRepository.existsByDocumento(1234567890L)).thenReturn(true);

        DuplicateResourceException ex =
                assertThrows(DuplicateResourceException.class, () -> userService.create(validRequest()));
        assertEquals("documento", ex.getField());
        verify(userRepository, never()).save(any());
    }

    // (c) create: duplicate correoElectronico rejected, naming the field
    @Test
    void createRejectsDuplicateCorreoElectronico() {
        when(userRepository.existsByCorreoElectronico("ana@example.com")).thenReturn(true);

        DuplicateResourceException ex =
                assertThrows(DuplicateResourceException.class, () -> userService.create(validRequest()));
        assertEquals("correoElectronico", ex.getField());
        verify(userRepository, never()).save(any());
    }

    // (d) create: XSS payload in primerNombre rejected by the sanitizer
    @Test
    void createRejectsScriptPayloadInPrimerNombre() {
        UserRequest request = new UserRequest(
                "<script>alert(1)</script>", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L,
                "3001234567", null, "ana@example.com",
                PLAINTEXT, null, null);

        assertThrows(SanitizationException.class, () -> userService.create(request));
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    // (d) triangulation: legitimate Unicode names pass through byte-identical
    @Test
    void createPreservesUnicodeNames() {
        when(passwordEncoder.encode(PLAINTEXT)).thenReturn(STORED_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRequest request = new UserRequest(
                "José Lía", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L,
                "3001234567", null, "ana@example.com",
                PLAINTEXT, Rol.ADMIN, TipoApoyo.alimentacion);

        userService.create(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("José Lía", saved.getValue().getPrimerNombre());
        assertEquals(Rol.ADMIN, saved.getValue().getRol());
        assertEquals(TipoApoyo.alimentacion, saved.getValue().getTipoApoyo());
    }

    // (g) create: trigger-managed columns are never carried by the application
    @Test
    void createNeverCarriesTriggerOwnedColumns() {
        when(passwordEncoder.encode(PLAINTEXT)).thenReturn(STORED_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(validRequest());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertNull(saved.getValue().getUltimaActualizacion(),
                "ultima_actualizacion is DB-owned (default + trigger); the service must never set it");
        assertNotNull(saved.getValue().getFechaRegistro(),
                "fecha_registro has no DB default in the mirrored schema; the service sets it on insert");
        assertNull(saved.getValue().getRol(),
                "rol stays null when the client omits it so trigger rolDefecto applies USUARIO");
    }

    // (e) update: without contrasena the stored hash stays byte-identical
    @Test
    void updateWithoutContrasenaKeepsStoredHashUntouched() {
        when(userRepository.findById(7)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRequest request = new UserRequest(
                "Ana", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L,
                "3119998877", null, "ana@example.com",
                null, null, null);

        userService.update(7, request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(STORED_HASH, saved.getValue().getContrasena());
        assertEquals("3119998877", saved.getValue().getCelular());
        verifyNoInteractions(passwordEncoder);
    }

    // (e) triangulation: update WITH contrasena re-validates and re-hashes
    @Test
    void updateWithContrasenaRehashes() {
        when(userRepository.findById(7)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.encode(PLAINTEXT)).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRequest request = new UserRequest(
                "Ana", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L,
                "3001234567", null, "ana@example.com",
                PLAINTEXT, null, null);

        userService.update(7, request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("new-hash", saved.getValue().getContrasena());
    }

    // (f) update: non-existent id → NotFoundException, nothing persisted
    @Test
    void updateOfMissingIdThrowsNotFound() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.update(99, validRequest()));
        verify(userRepository, never()).save(any());
    }

    // 6.5 RED: create/update/delete flows report operational events to the
    // system logger, with component + action + affected id only.
    @Test
    void createLogsComponentActionAndAffectedId() {
        when(passwordEncoder.encode(PLAINTEXT)).thenReturn(STORED_HASH);
        User saved = existingUser();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        userService.create(validRequest());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(logService).logInfo(eq("UserService"), message.capture());
        assertTrue(message.getValue().contains("created"), "message must name the action");
        assertTrue(message.getValue().contains("id=7"), "message must carry only the affected id");
        assertFalse(message.getValue().contains(PLAINTEXT), "plaintext must never be logged");
        assertFalse(message.getValue().contains("$2b$"), "the BCrypt hash must never be logged");
        assertFalse(message.getValue().contains("ana@example.com"),
                "log entries carry only component + action + id, not payloads");
    }

    @Test
    void updateLogsComponentActionAndAffectedId() {
        when(userRepository.findById(7)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRequest request = new UserRequest(
                "Ana", null, "Garcia", null,
                TipoDocumento.CC, 1234567890L,
                "3119998877", null, "ana@example.com",
                null, null, null);

        userService.update(7, request);

        verify(logService).logInfo(eq("UserService"),
                org.mockito.ArgumentMatchers.argThat(m -> m.contains("updated") && m.contains("id=7")));
    }

    @Test
    void deleteLogsComponentActionAndAffectedId() {
        when(userRepository.existsById(7)).thenReturn(true);

        userService.delete(7);

        verify(logService).logInfo(eq("UserService"),
                org.mockito.ArgumentMatchers.argThat(m -> m.contains("deleted") && m.contains("id=7")));
    }

    @Test
    void deleteLogsNothingWhenTheIdDoesNotExist() {
        when(userRepository.existsById(99)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> userService.delete(99));
        verifyNoInteractions(logService);
    }

    @Test
    void findByIdReturnsTheMappedUser() {
        when(userRepository.findById(7)).thenReturn(Optional.of(existingUser()));

        UserResponse response = userService.findById(7);

        assertEquals(7, response.id());
        assertEquals("Ana", response.primerNombre());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), response.ultimaActualizacion());
    }

    @Test
    void findByIdThrowsNotFoundForMissingId() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.findById(99));
    }

    @Test
    void deleteRemovesExistingUser() {
        when(userRepository.existsById(7)).thenReturn(true);

        userService.delete(7);

        verify(userRepository).deleteById(7);
    }

    @Test
    void deleteThrowsNotFoundForMissingId() {
        when(userRepository.existsById(99)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> userService.delete(99));
        verify(userRepository, never()).deleteById(any());
    }

    // --- Phase 5: read-side search/pagination delegation (spec user-search-pagination) ---

    @Test
    void findAllUsersDelegatesThePageableAndMapsToPasswordFreeResponses() {
        Pageable request = PageRequest.of(0, 7);
        when(userRepository.findAll(request))
                .thenReturn(new PageImpl<>(List.of(existingUser()), request, 8));

        PagedModel<UserResponse> page = userService.findAllUsers(request);

        assertEquals(1, page.getContent().size());
        assertEquals(8, page.getMetadata().totalElements());
        assertEquals("Ana", page.getContent().getFirst().primerNombre());
    }

    @Test
    void searchAndForwardsBothCriteriaAndPageableToTheDerivedQuery() {
        Pageable request = PageRequest.of(1, 7);
        when(userRepository.findByPrimerNombreAndDocumento("Ana", 1234567890L, request))
                .thenReturn(Page.empty(request));

        PagedModel<UserResponse> page = userService.searchAnd("Ana", 1234567890L, request);

        assertTrue(page.getContent().isEmpty());
        verify(userRepository).findByPrimerNombreAndDocumento("Ana", 1234567890L, request);
    }

    @Test
    void searchOrPassesTheTermToBothNameBranchesAndTheParsedDocumento() {
        Pageable request = PageRequest.of(0, 7);
        when(userRepository.findByPrimerNombreOrPrimerApellidoOrDocumento("1122334455", "1122334455", 1122334455L, request))
                .thenReturn(Page.empty(request));

        PagedModel<UserResponse> page = userService.searchOr("1122334455", 1122334455L, request);

        assertTrue(page.getContent().isEmpty());
        verify(userRepository).findByPrimerNombreOrPrimerApellidoOrDocumento("1122334455", "1122334455", 1122334455L, request);
    }
}
