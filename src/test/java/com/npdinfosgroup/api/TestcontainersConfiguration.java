package com.npdinfosgroup.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Levanta un PostgreSQL real en Docker para los tests que cargan el contexto de Spring.
 * <p>
 * {@link ServiceConnection} conecta el datasource al contenedor automáticamente, así los tests no
 * dependen de una base de datos local ni de las variables {@code DB_*}. Se usa con
 * {@code @Import(TestcontainersConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * Contenedor con la misma imagen que usa {@code compose.yaml}.
	 *
	 * @return el contenedor de PostgreSQL, cuyo ciclo de vida gestiona Spring
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

}
