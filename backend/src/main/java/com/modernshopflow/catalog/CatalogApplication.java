package com.modernshopflow.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.modernshopflow.catalog", "com.shopflow.backend"})
@EntityScan(basePackages = "com.shopflow.backend.entity")
@EnableJpaRepositories(basePackages = "com.shopflow.backend.repository")
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
