package com.timetracking.user.service;

import com.timetracking.user.domain.UserAccount;
import com.timetracking.user.repository.UserAccountRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthService implements UserDetailsService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        Instant now = Instant.now();
        UserAccount account = new UserAccount(
                null,
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                Set.of(DEFAULT_ROLE),
                now,
                now
        );

        try {
            return userAccountRepository.save(account);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateEmailException(normalizedEmail);
        }
    }

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        UserAccount account = userAccountRepository.findByEmail(normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .authorities(account.getRoles().stream()
                                     .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                                     .toArray(String[]::new))
                .build();
    }

    public static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

