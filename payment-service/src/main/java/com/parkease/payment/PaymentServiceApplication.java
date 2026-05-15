package com.parkease.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner migrateStatusColumn(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                log.info("Executing database migration to alter pass_balances.status column to VARCHAR...");
                jdbcTemplate.execute("ALTER TABLE pass_balances MODIFY COLUMN status VARCHAR(50) NOT NULL");
                log.info("Migration successful.");
            } catch (Exception e) {
                log.warn("Migration failed or already applied: {}", e.getMessage());
            }
        };
    }
}

