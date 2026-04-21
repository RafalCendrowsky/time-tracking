package com.timetracking.auth.web.user;

import com.timetracking.auth.service.UserService;
import com.timetracking.auth.web.user.dto.UpdateProfileRequest;
import com.timetracking.auth.web.user.dto.UpdateRolesRequest;
import com.timetracking.auth.web.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> searchUsers(@RequestParam(required = false) String query) {
        return userService.searchUsers(query)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return UserResponse.from(userService.updateRoles(id, request.roles()));
    }

    @PutMapping("/me/profile")
    public UserResponse updateProfile(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody UpdateProfileRequest request
    ) {
        var userId = UUID.fromString(principal.getUsername());
        return UserResponse.from(
                userService.updateProfile(userId, request.firstName(), request.lastName()));
    }
}

