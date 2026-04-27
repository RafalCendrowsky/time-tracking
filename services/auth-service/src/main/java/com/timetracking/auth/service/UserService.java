package com.timetracking.auth.service;

import com.timetracking.auth.config.principal.InternalUserPrincipal;
import com.timetracking.auth.config.principal.UserPrincipal;
import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.exception.DuplicateEmailException;
import com.timetracking.auth.model.domain.UserAccount;
import com.timetracking.auth.model.repository.UserAccountRepository;
import com.timetracking.auth.web.user.dto.RegisterUserResponse;
import com.timetracking.auth.web.user.dto.UserResponse;
import com.timetracking.auth.web.user.dto.UserShortResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import static com.timetracking.auth.util.NormalizationUtil.normalizeEmail;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private static final UserRole DEFAULT_ROLE = UserRole.USER;
    private static final int SEARCH_LIMIT = 20;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserAccount> searchUsers(String query, UserPrincipal principal) {
        Pageable page = PageRequest.of(0, SEARCH_LIMIT);
        return principal.isAdmin() ?
               userAccountRepository.searchByEmailOrName(query, page) :
               userAccountRepository.searchByEmailOrName(query, principal.organizationId(), page);
    }

    public Optional<UserAccount> findByEmail(String email) {
        return userAccountRepository.findByEmail(normalizeEmail(email));
    }

    public UserAccount findById(UUID id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User %s not found".formatted(id)));
    }

    public UserAccount findById(UUID id, UserPrincipal principal) {
        if (principal.isAdmin()) {
            return findById(id);
        }
        var orgId = principal.organizationId();
        return userAccountRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new UsernameNotFoundException("User %s not found in %s".formatted(id, orgId)));
    }


    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        var account = findByEmail(normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new InternalUserPrincipal(account);
    }

    public UserAccount updateRoles(UUID id, Set<UserRole> roles) {
        var account = findById(id);
        account.setRoles(roles);
        account.setUpdatedAt(Instant.now());
        return userAccountRepository.save(account);
    }

    public UserAccount updateProfile(UUID id, String firstName, String lastName) {
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
            UUID organizationId,
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
            UUID organizationId,
            String firstName,
            String lastName
    ) {
        var now = Instant.now();
        return new UserAccount(
                UUID.randomUUID(),
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
