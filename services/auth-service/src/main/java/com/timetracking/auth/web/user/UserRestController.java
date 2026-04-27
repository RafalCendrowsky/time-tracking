package com.timetracking.auth.web.user;

import com.timetracking.auth.config.principal.UserPrincipal;
import com.timetracking.auth.service.UserService;
import com.timetracking.auth.web.user.dto.UpdateProfileRequest;
import com.timetracking.auth.web.user.dto.UpdateRolesRequest;
import com.timetracking.auth.web.user.dto.UserResponse;
import com.timetracking.auth.web.user.dto.UserShortResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;

    @GetMapping
    public List<UserShortResponse> searchUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String query
    ) {
        return userService.searchUsers(query, principal)
                .stream()
                .map(UserShortResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public UserShortResponse getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return UserShortResponse.from(userService.findById(id, principal));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUserDetails(@PathVariable UUID id) {
        return UserResponse.from(userService.findById(id));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return UserResponse.from(userService.updateRoles(id, request.roles()));
    }

    @PutMapping("/me/profile")
    public UserResponse updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateProfileRequest request
    ) {
        return UserResponse.from(userService.updateProfile(
                principal.userId(),
                request.firstName(),
                request.lastName()
        ));
    }
}
