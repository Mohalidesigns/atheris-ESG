package com.esg.config;

import com.esg.model.entity.User;
import com.esg.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Fix the demo user passwords with properly encoded hashes
        String encodedPassword = passwordEncoder.encode("demo123");
        userRepository.findAll().forEach(user -> {
            user.setPasswordHash(encodedPassword);
            userRepository.save(user);
        });
    }
}
