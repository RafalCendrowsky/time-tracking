package com.timetracking.auth.service;

import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.dto.InternalUserPrincipal;
import com.timetracking.auth.exception.DuplicateEmailException;
import com.timetracking.auth.model.domain.UserAccount;
import com.timetracking.auth.model.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.timetracking.auth.util.NormalizationUtil.normalizeEmail;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private static final UserRole DEFAULT_ROLE = UserRole.USER;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<UserAccount> findByEmail(String email) {
        return userAccountRepository.findByEmail(normalizeEmail(email));
    }

    public UserAccount findById(String id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }

    public List<UserAccount> searchUsers(String query) {
        if (query == null || query.isBlank()) {
            return userAccountRepository.findAll();
        }
        return userAccountRepository.searchByEmailOrName(query.trim());
    }


    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        var account = findByEmail(normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new InternalUserPrincipal(account);
    }

    public UserAccount updateRoles(String id, Set<UserRole> roles) {
        var account = findById(id);
        account.setRoles(roles);
        account.setUpdatedAt(Instant.now());
        return userAccountRepository.save(account);
    }

    public UserAccount updateProfile(String id, String firstName, String lastName) {
        var account = findById(id);
        account.setFirstName(firstName);
        account.setLastName(lastName);
        account.setUpdatedAt(Instant.now());
        return userAccountRepository.save(account);
    }

    public UserAccount register(String email, String rawPassword, String firstName, String lastName) {
        var normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        var account = newUserAccount(normalizedEmail, passwordEncoder.encode(rawPassword), null, firstName, lastName);

        try {
            return userAccountRepository.save(account);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateEmailException(normalizedEmail);
        }
    }

    public UserAccount resolveOrProvisionExternalAccount(
            String email,
            String organizationId,
            String firstName,
            String lastName
    ) {
        var normalizedEmail = normalizeEmail(email);

        var account = userAccountRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> newUserAccount(normalizedEmail, null, organizationId, firstName, lastName));

        boolean changed = false;
        if (!Objects.equals(account.getOrganizationId(), organizationId)) {
            account.setOrganizationId(organizationId);
            changed = true;
        }
        if (firstName != null && !Objects.equals(account.getFirstName(), firstName)) {
            account.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !Objects.equals(account.getLastName(), lastName)) {
            account.setLastName(lastName);
            changed = true;
        }
        if (changed) {
            account.setUpdatedAt(Instant.now());
        }
        return userAccountRepository.save(account);
    }

    private UserAccount newUserAccount(
            String email,
            String password,
            String organizationId,
            String firstName,
            String lastName
    ) {
        var now = Instant.now();
        return new UserAccount(
                null,
                email,
                password,
                organizationId,
                Set.of(DEFAULT_ROLE),
                firstName,
                lastName,
                now,
                now
        );
    }
}
