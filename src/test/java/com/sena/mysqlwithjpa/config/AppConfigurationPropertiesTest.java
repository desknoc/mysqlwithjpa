package com.sena.mysqlwithjpa.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration discipline contract: the committed application.properties must
 * externalize every environment-specific value through ${...} placeholders and
 * must never carry literal credentials or schema-mutating Hibernate settings.
 */
class AppConfigurationPropertiesTest {

    private static final Properties PROPS = loadFromClasspath();

    private static Properties loadFromClasspath() {
        try (InputStream in = AppConfigurationPropertiesTest.class
                .getResourceAsStream("/application.properties")) {
            assertThat(in).as("application.properties must be on the classpath").isNotNull();
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application.properties", e);
        }
    }

    @Test
    void datasourceCredentialsAreExternalizedThroughPlaceholders() {
        assertThat(PROPS.getProperty("spring.datasource.username"))
                .as("datasource username must be a placeholder, never a literal")
                .isEqualTo("${MYSQL_USER}");
        assertThat(PROPS.getProperty("spring.datasource.password"))
                .as("datasource password must be a placeholder, never a literal")
                .isEqualTo("${MYSQL_PASSWORD}");
    }

    @Test
    void datasourceUrlIsBuiltFromEnvironmentPlaceholders() {
        assertThat(PROPS.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"
                        + "?ssl-mode=${MYSQL_SSL_MODE:REQUIRED}");
    }

    @Test
    void sslModeDefaultsToRequiredAndIsNeverDisabled() {
        String url = PROPS.getProperty("spring.datasource.url");
        assertThat(url).contains("ssl-mode=${MYSQL_SSL_MODE:REQUIRED}");
        assertThat(url.toUpperCase()).doesNotContain("ssl-mode=DISABLED");

        String committedConfig = readRawProperties();
        assertThat(committedConfig.toUpperCase())
                .as("committed config must never disable SSL for the production target")
                .doesNotContain("SSL-MODE=DISABLED");
    }

    @Test
    void hibernateSchemaManagementIsValidateOnly() {
        assertThat(PROPS.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("Hibernate must never alter the trigger-owned schema")
                .isEqualTo("validate");
    }

    @Test
    void openInViewIsDisabled() {
        assertThat(PROPS.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void mongoLoggingPropertiesAreExternalized() {
        assertThat(PROPS.getProperty("spring.data.mongodb.uri"))
                .isEqualTo("${MONGODB_URI}");
        assertThat(PROPS.getProperty("app.logging.mongo.enabled"))
                .isEqualTo("${MONGO_LOGGING_ENABLED:true}");
        assertThat(PROPS.getProperty("app.ratelimit.capacity"))
                .isEqualTo("${RATE_LIMIT_CAPACITY:100}");
        assertThat(PROPS.getProperty("app.ratelimit.refill-per-minute"))
                .isEqualTo("${RATE_LIMIT_REFILL_PER_MINUTE:100}");
    }

    @Test
    void noLiteralCredentialValuesAppearInConfiguration() {
        String raw = readRawProperties();
        assertThat(raw)
                .doesNotContain("jdbc:mysql://localhost")
                .doesNotContain("spring.datasource.username=root")
                .doesNotContain("verysecret");
        assertThat(raw).doesNotContainPattern("(?m)^spring\\.datasource\\.password=[^$].*$");
    }

    private static String readRawProperties() {
        try (InputStream in = AppConfigurationPropertiesTest.class
                .getResourceAsStream("/application.properties")) {
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read application.properties", e);
        }
    }
}
