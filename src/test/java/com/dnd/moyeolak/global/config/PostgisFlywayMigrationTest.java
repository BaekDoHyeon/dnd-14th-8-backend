package com.dnd.moyeolak.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgisFlywayMigrationTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:15-3.3")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("moyeolak")
            .withUsername("moyeolak")
            .withPassword("moyeolak");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void shouldApplyFlywayMigrationsAndValidatePostgisSchema() {
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class
        );
        Integer stationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stations", Integer.class);
        String postgisVersion = jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class);

        assertThat(appliedMigrationCount).isEqualTo(3);
        assertThat(stationCount).isPositive();
        assertThat(postgisVersion).isNotBlank();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
    }
}
