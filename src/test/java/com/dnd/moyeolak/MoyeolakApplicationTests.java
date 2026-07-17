package com.dnd.moyeolak;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles("test")
class MoyeolakApplicationTests {

    private final Environment environment;
    private final Map<String, DataSource> dataSources;
    private final Map<String, EntityManagerFactory> entityManagerFactories;
    private final Map<String, PlatformTransactionManager> transactionManagers;

    @Autowired
    MoyeolakApplicationTests(
            Environment environment,
            Map<String, DataSource> dataSources,
            Map<String, EntityManagerFactory> entityManagerFactories,
            Map<String, PlatformTransactionManager> transactionManagers
    ) {
        this.environment = environment;
        this.dataSources = dataSources;
        this.entityManagerFactories = entityManagerFactories;
        this.transactionManagers = transactionManagers;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldUseSingleDatasourceAutoConfiguration() {
        assertThat(dataSources).containsOnlyKeys("dataSource");
        assertThat(entityManagerFactories).containsOnlyKeys("entityManagerFactory");
        assertThat(transactionManagers).containsOnlyKeys("transactionManager");
    }

    @Test
    void shouldNotUsePrimarySecondaryDatasourceProperties() {
        assertThat(environment.getProperty("spring.datasource.primary.url")).isNull();
        assertThat(environment.getProperty("spring.datasource.secondary.url")).isNull();
        assertThat(environment.getProperty("spring.jpa.primary.ddl-auto")).isNull();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
    }
}
