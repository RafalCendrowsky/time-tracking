package com.timetracking.user.service;

import com.timetracking.user.dto.InternalUserPrincipal;
import com.timetracking.user.exception.DuplicateEmailException;
import com.timetracking.user.model.domain.UserAccount;
import com.timetracking.user.model.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.timetracking.user.util.NormalizationUtil.normalizeEmail;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private static final String DEFAULT_ROLE = "USER";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<UserAccount> findByEmail(String email) {
        return userAccountRepository.findByEmail(normalizeEmail(email));
    }

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        UserAccount account = userAccountRepository.findByEmail(normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new InternalUserPrincipal(account);
    }

    public UserAccount register(String email, String rawPassword) {
        var normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        var account = newUserAccount(normalizedEmail, passwordEncoder.encode(rawPassword), null);

        try {
            return userAccountRepository.save(account);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateEmailException(normalizedEmail);
        }
    }

    public UserAccount resolveOrProvisionExternalAccount(String email, String organizationId) {
        var normalizedEmail = normalizeEmail(email);

        var account = userAccountRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> newUserAccount(normalizedEmail, null, organizationId));

        if (!Objects.equals(account.getOrganizationId(), organizationId)) {
            account.setOrganizationId(organizationId);
            account.setUpdatedAt(Instant.now());
        }
        return userAccountRepository.save(account);
    }

    private UserAccount newUserAccount(String email, String password, String organizationId) {
        var now = Instant.now();
        return new UserAccount(null, email, password, organizationId, Set.of(DEFAULT_ROLE), now, now);
    }
}
