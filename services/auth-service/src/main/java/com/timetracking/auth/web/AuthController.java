package com.timetracking.auth.web;

import com.timetracking.auth.dto.LoginOption;
import com.timetracking.auth.service.LoginOptionService;
import com.timetracking.auth.service.UserService;
import com.timetracking.auth.web.user.dto.RegisterUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService authService;
    private final LoginOptionService loginOptionService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserRequest request) {
        authService.register(request.email(), request.password(), request.firstName(), request.lastName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/login-options")
    public ResponseEntity<List<LoginOption>> loginOptions(@RequestParam String email) {
        return ResponseEntity.ok(loginOptionService.findLoginOptions(email));
    }

    @GetMapping("/login/internal")
    public ResponseEntity<Void> internalLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/login"))
                .build();
    }

    @GetMapping("/login/external/{organizationId}")
    public ResponseEntity<Void> externalLogin(@PathVariable UUID organizationId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/" + organizationId))
                .build();
    }
}
