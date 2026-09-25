package com.sena.mysqlwithjpa.service;

import com.sena.mysqlwithjpa.dto.UserRequest;
import com.sena.mysqlwithjpa.dto.UserResponse;
import com.sena.mysqlwithjpa.entity.User;
import com.sena.mysqlwithjpa.repository.UserRepository;
import com.sena.mysqlwithjpa.service.exception.DuplicateResourceException;
import com.sena.mysqlwithjpa.service.exception.NotFoundException;
import com.sena.mysqlwithjpa.service.log.LogService;
import com.sena.mysqlwithjpa.util.Sanitizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * User CRUD orchestration. Responsibilities, in order: sanitize free-text
 * inputs (400 on rejection), validate the plaintext password BEFORE it ever
 * reaches the encoder (minimum 8 chars), pre-check uniqueness on
 * {@code documento} / {@code correoElectronico} (409), hash with the
 * application {@link PasswordEncoder} (BCrypt, cost 10), and persist honoring
 * the DB trigger contract: the service never writes {@code ultimaActualizacion}
 * (DB default + {@code actualizarFechaUsuario} trigger), sets
 * {@code fechaRegistro} once on insert (the schema has no DB default for it),
 * and leaves {@code rol} null when the client omits it so {@code rolDefecto}
 * applies USUARIO via the entity's {@code @DynamicInsert}.
 * Successful create/update/delete operations are reported to the system logger
 * with component + action + affected id ONLY — never payloads, credentials,
 * or hashes (spec mongodb-logging: no secrets in logs).
 */
@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String COMPONENT = "UserService";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LogService logService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       LogService logService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.logService = logService;
    }

    public UserResponse create(UserRequest request) {
        sanitize(request);
        requireValidPlaintext(request.contrasena());
        requireUniqueDocumento(request.documento());
        requireUniqueCorreo(request.correoElectronico());

        User user = new User();
        applyMutableFields(user, request);
        user.setContrasena(passwordEncoder.encode(request.contrasena()));
        user.setFechaRegistro(LocalDateTime.now());

        UserResponse response = UserResponse.from(userRepository.save(user));
        logService.logInfo(COMPONENT, "user created id=" + response.id());
        return response;
    }

    public UserResponse findById(Integer id) {
        return UserResponse.from(findUser(id));
    }

    public UserResponse update(Integer id, UserRequest request) {
        sanitize(request);
        User user = findUser(id);
        applyMutableFields(user, request);
        if (request.contrasena() != null) {
            requireValidPlaintext(request.contrasena());
            user.setContrasena(passwordEncoder.encode(request.contrasena()));
        }
        // fechaRegistro / ultimaActualizacion are never touched here.
        UserResponse response = UserResponse.from(userRepository.save(user));
        logService.logInfo(COMPONENT, "user updated id=" + response.id());
        return response;
    }

    public void delete(Integer id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User", id);
        }
        userRepository.deleteById(id);
        logService.logInfo(COMPONENT, "user deleted id=" + id);
    }

    private User findUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
    }

    private static void requireValidPlaintext(String plaintext) {
        if (plaintext == null || plaintext.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "contrasena must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private static void sanitize(UserRequest request) {
        Sanitizer.requireClean("primerNombre", request.primerNombre());
        Sanitizer.requireClean("segundoNombre", request.segundoNombre());
        Sanitizer.requireClean("primerApellido", request.primerApellido());
        Sanitizer.requireClean("segundoApellido", request.segundoApellido());
        Sanitizer.requireClean("celular", request.celular());
        Sanitizer.requireClean("grupoFormacion", request.grupoFormacion());
        Sanitizer.requireClean("correoElectronico", request.correoElectronico());
    }

    private void requireUniqueDocumento(Long documento) {
        if (userRepository.existsByDocumento(documento)) {
            throw new DuplicateResourceException("documento", documento);
        }
    }

    private void requireUniqueCorreo(String correoElectronico) {
        if (userRepository.existsByCorreoElectronico(correoElectronico)) {
            throw new DuplicateResourceException("correoElectronico", correoElectronico);
        }
    }

    private static void applyMutableFields(User user, UserRequest request) {
        user.setPrimerNombre(request.primerNombre());
        user.setSegundoNombre(request.segundoNombre());
        user.setPrimerApellido(request.primerApellido());
        user.setSegundoApellido(request.segundoApellido());
        user.setTipoDocumento(request.tipoDocumento());
        user.setDocumento(request.documento());
        user.setCelular(request.celular());
        user.setGrupoFormacion(request.grupoFormacion());
        user.setCorreoElectronico(request.correoElectronico());
        // rol stays whatever the client sent (null on insert → DB trigger default).
        user.setRol(request.rol());
        user.setTipoApoyo(request.tipoApoyo());
    }
}
