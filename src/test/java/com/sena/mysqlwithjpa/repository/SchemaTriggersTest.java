package com.sena.mysqlwithjpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract between the committed test schema and the DBA-owned production
 * `usuario` table: schema.sql must reproduce the production column set AND its
 * three triggers, and Hibernate must validate against it instead of generating
 * any DDL of its own.
 *
 * Runs against a Testcontainers MySQL 8.4 container initialized from
 * src/test/resources/schema.sql. Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SchemaTriggersTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private Environment environment;

    @Test
    void usuarioTableExistsWithTheProductionColumnSet() {
        Number tableCount = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario'")
                .getSingleResult();
        assertThat(tableCount.longValue())
                .as("schema.sql must create the usuario table")
                .isEqualTo(1);

        List<Object> columns = entityManager.createNativeQuery(
                        "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario'")
                .getResultList();
        assertThat(columns)
                .as("usuario must carry exactly the production column set")
                .containsExactlyInAnyOrder(
                        "id_usuario", "primer_nombre", "segundo_nombre", "primer_apellido",
                        "segundo_apellido", "tipo_documento", "documento", "celular",
                        "grupo_formacion", "correo_electronico", "contrasena", "rol",
                        "tipo_apoyo", "fecha_registro", "ultima_actualizacion");
    }

    @Test
    void allThreeProductionTriggersAreRegistered() {
        List<Object> triggers = entityManager.createNativeQuery(
                        "SELECT TRIGGER_NAME FROM information_schema.TRIGGERS "
                                + "WHERE TRIGGER_SCHEMA = DATABASE()")
                .getResultList();
        assertThat(triggers)
                .as("schema.sql must register the three DBA-owned triggers")
                .contains("rolDefecto", "actualizarFechaUsuario", "validarContrasena");
    }

    @Test
    void hibernateValidatesAndNeverGeneratesSchema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("Hibernate must validate the trigger-owned schema, never alter it")
                .isEqualTo("validate");
        // The context boot itself is the assertion: with ddl-auto=validate the
        // EntityManagerFactory only starts when the mapping matches schema.sql,
        // and no CREATE/ALTER statements are ever issued by Hibernate.
    }
}
