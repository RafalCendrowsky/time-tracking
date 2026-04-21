package com.timetracking.auth.service;

import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.exception.DuplicateEmailException;
import com.timetracking.auth.model.domain.UserAccount;
import com.timetracking.auth.model.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(userAccountRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return account;
        });

        UserAccount saved = userService.register(" Alice@Example.com ", "password123", "Alice", "Something");

        var captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(captor.getValue().getRoles()).containsExactly(UserRole.USER);
        assertThat(saved.getId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userAccountRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("alice@example.com", "password123", "Alice", "Something"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void loadUserByUsernameMapsRolesToAuthorities() {
        UserAccount account = new UserAccount(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice@example.com",
                "hashed",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                Set.of(UserRole.USER, UserRole.ADMIN),
                "Alice",
                "Something",
                Instant.now(),
                Instant.now()
        );
        when(userAccountRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(account));

        UserDetails userDetails = userService.loadUserByUsername("Alice@Example.com");

        assertThat(userDetails.getUsername()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void resolveOrProvisionExternalAccountCreatesNewOrganizationBackedAccount() {
        when(userAccountRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount account =
                userService.resolveOrProvisionExternalAccount(
                        "alice@example.com",
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Alice",
                        "Something"
                );

        assertThat(account.getEmail()).isEqualTo("alice@example.com");
        assertThat(account.getPasswordHash()).isNull();
        assertThat(account.getOrganizationId()).isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    }
}

