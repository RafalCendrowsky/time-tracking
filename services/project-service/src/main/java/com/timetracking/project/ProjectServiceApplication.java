package com.timetracking.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class AuthServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}
