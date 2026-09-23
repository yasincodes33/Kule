package com.AgentSaasAplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.AgentSaasAplication")
@EnableAsync
@EnableScheduling
@EnableKafka
public class AgentSaasAplicationApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentSaasAplicationApplication.class, args);
	}

}
