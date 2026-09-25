package com.sena.mysqlwithjpa.repository;

import com.sena.mysqlwithjpa.TestcontainersConfiguration;
import com.sena.mysqlwithjpa.entity.Rol;
import com.sena.mysqlwithjpa.entity.TipoApoyo;
import com.sena.mysqlwithjpa.entity.TipoDocumento;
import com.sena.mysqlwithjpa.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity round-trip and database-trigger ownership tests against Testcontainers
 * MySQL 8.4 initialized from src/test/resources/schema.sql (real DDL + the
 * three DBA-owned triggers). Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    // Real 60-char BCrypt hash of "password", as stored by the Express backend.
    private static final String BCRYPT_HASH =
            "$2b$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsAndReadsBackAllMappedColumnsWithCorrectJavaTypes() {
        User user = validUser(3000000000L, "ana@example.com");
        user.setSegundoNombre("Lia");
        user.setSegundoApellido("Torres");
        user.setGrupoFormacion("ADSO");
        user.setTipoApoyo(TipoApoyo.transporte);

        User saved = userRepository.save(user);
        entityManager.clear();

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        User read = found.get();
        assertThat(read.getId()).isNotNull();
        assertThat(read.getDocumento())
                .as("documento is BIGINT and must round-trip as Long beyond int range")
                .isInstanceOf(Long.class)
                .isEqualTo(3000000000L);
        assertThat(read.getPrimerNombre()).isEqualTo("Ana");
        assertThat(read.getSegundoNombre()).isEqualTo("Lia");
        assertThat(read.getPrimerApellido()).isEqualTo("Garcia");
        assertThat(read.getSegundoApellido()).isEqualTo("Torres");
        assertThat(read.getTipoDocumento()).isEqualTo(TipoDocumento.CC);
        assertThat(read.getCelular()).isEqualTo("3001234567");
        assertThat(read.getGrupoFormacion()).isEqualTo("ADSO");
        assertThat(read.getCorreoElectronico()).isEqualTo("ana@example.com");
        assertThat(read.getContrasena()).isEqualTo(BCRYPT_HASH);
        assertThat(read.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(read.getTipoApoyo()).isEqualTo(TipoApoyo.transporte);
        assertThat(read.getFechaRegistro()).isNotNull();
    }

    @Test
    void ultimaActualizacionIsSetOnInsertAndMaintainedByTheUpdateTrigger() throws Exception {
        User user = validUser(1000000001L, "trigger-update@example.com");

        User saved = userRepository.save(user);
        assertThat(saved.getUltimaActualizacion())
                .as("aplicacion nunca envia ultima_actualizacion (insertable=false)")
                .isNull();

        Object onInsert = storedUltimaActualizacion(saved.getId());
        assertThat(onInsert)
                .as("DB default populates ultima_actualizacion on insert")
                .isNotNull();

        // Deflake: ultima_actualizacion is datetime(6); NOW() resolution still
        // deserves a gap so the trigger-updated value provably differs.
        Thread.sleep(25);
        saved.setPrimerNombre("Valentina");
        userRepository.save(saved);
        entityManager.flush();
        entityManager.clear();
        assertThat(saved.getUltimaActualizacion())
                .as("aplicacion nunca envia ultima_actualizacion (updatable=false)")
                .isNull();

        Object onUpdate = storedUltimaActualizacion(saved.getId());
        assertThat(onUpdate)
                .as("trigger actualizarFechaUsuario rewrites ultima_actualizacion on update")
                .isNotEqualTo(onInsert);
    }

    @Test
    void nullRolIsDefaultedToUsuarioByTrigger() {
        User user = validUser(1000000002L, "rol-default@example.com");
        user.setRol(null);

        User saved = userRepository.save(user);
        entityManager.clear();

        assertThat(saved.getRol())
                .as("with @DynamicInsert the application must not carry a null rol")
                .isNull();
        User read = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(read.getRol())
                .as("trigger rolDefecto applies the USUARIO default")
                .isEqualTo(Rol.USUARIO);
    }

    @Test
    void explicitRolAdminIsPersistedUnchanged() {
        User user = validUser(1000000003L, "rol-admin@example.com");
        user.setRol(Rol.ADMIN);

        User saved = userRepository.save(user);
        entityManager.clear();

        User read = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(read.getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void bcryptHashPassesValidarContrasenaTriggerAndReadsBackByteIdentical() {
        assertThat(BCRYPT_HASH).hasSize(60).startsWith("$2b$10$");

        User user = validUser(1000000004L, "bcrypt@example.com");
        user.setContrasena(BCRYPT_HASH);

        User saved = userRepository.save(user);
        entityManager.clear();

        User read = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(read.getContrasena())
                .as("the persisted value is the BCrypt hash, byte-identical")
                .isEqualTo(BCRYPT_HASH);
    }

    private static User validUser(Long documento, String correoElectronico) {
        User user = new User();
        user.setPrimerNombre("Ana");
        user.setPrimerApellido("Garcia");
        user.setTipoDocumento(TipoDocumento.CC);
        user.setDocumento(documento);
        user.setCelular("3001234567");
        user.setCorreoElectronico(correoElectronico);
        user.setContrasena(BCRYPT_HASH);
        user.setRol(Rol.USUARIO);
        user.setFechaRegistro(LocalDateTime.now());
        return user;
    }

    private Object storedUltimaActualizacion(Integer id) {
        return entityManager.createNativeQuery(
                        "SELECT ultima_actualizacion FROM usuario WHERE id_usuario = ?1")
                .setParameter(1, id)
                .getSingleResult();
    }
}
