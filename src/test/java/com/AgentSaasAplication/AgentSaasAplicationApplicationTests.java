package com.AgentSaasAplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// application.yml üretim profilinde api-key-secret/jwt.secret için varsayılan taşımıyor;
// test context'i kendi değerlerini burada tanımlıyor (izole, kalıcı olmayan bir veritabanı).
@TestPropertySource(properties = {
		"app.security.api-key-secret=Nob4gLqy5BdHw4+JVQLwPOn/pZPAwRHAbsNLrhT8vrw=",
		"app.jwt.secret=u0jPk8Yj54/bZdstnsXwYA/HSwg07yaSPdXGOh3dURg="
})
@SpringBootTest
@ActiveProfiles("test")  // testler docker-compose veritabanina baglanir (bkz. application-test.properties)
class AgentSaasAplicationApplicationTests {

	@Test
	void contextLoads() {
	}

}
