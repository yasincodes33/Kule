package com.AgentSaasAplication.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(order = 100)
public class TransactionManagementConfig {
}
