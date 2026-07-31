package com.khoi.ewallet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.profiles.active=local",
		"spring.autoconfigure.exclude=org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcAutoConfiguration",
		"management.health.db.enabled=false",
		"jwt.secret=test-only-secret!that-is-at-least-32-bytes-long"
})
class EwalletApplicationTests {

	@MockitoBean
	JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

}
