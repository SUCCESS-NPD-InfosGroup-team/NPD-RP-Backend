package com.npdinfosgroup.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifica que el contexto completo de la aplicación arranca contra un PostgreSQL real.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApiApplicationTests {

	/**
	 * Falla si algún bean, la configuración o las migraciones de Flyway impiden arrancar la aplicación.
	 */
	@Test
	void contextLoads() {
	}

}
