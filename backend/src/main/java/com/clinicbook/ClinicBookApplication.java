package com.clinicbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// The API is stateless JWT only, so Boot's generated in-memory user is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ClinicBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicBookApplication.class, args);
    }
}
