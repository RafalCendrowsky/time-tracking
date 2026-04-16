package com.timetracking.user.web;

import com.timetracking.user.dto.LoginOption;
import com.timetracking.user.model.domain.UserAccount;
import com.timetracking.user.service.LoginOptionService;
import com.timetracking.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService authService;

    @MockitoBean
    private LoginOptionService loginOptionService;

    @Test
    void registerReturnsCreatedUser() throws Exception {
        UserAccount userAccount = new UserAccount(
                "user-1",
                "alice@example.com",
                "hashed",
                null,
                Set.of("USER"),
                Instant.parse("2026-04-16T12:00:00Z"),
                Instant.parse("2026-04-16T12:00:00Z")
        );
        when(authService.register(anyString(), anyString())).thenReturn(userAccount);

        mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                 {"email":"Alice@Example.com","password":"password123"}
                                                 """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", equalTo("alice@example.com")))
                .andExpect(jsonPath("$.roles[0]", equalTo("USER")));
    }

    @Test
    void loginOptionsReturnsConfiguredLoginRoutes() throws Exception {
        when(loginOptionService.findLoginOptions("alice@example.com")).thenReturn(List.of(
                LoginOption.internal("/api/auth/login/internal"),
                LoginOption.external("org-1", "Acme", "/api/auth/login/external/org-1")
        ));

        mockMvc.perform(get("/api/auth/login-options")
                                .param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type", equalTo("INTERNAL")))
                .andExpect(jsonPath("$[1].organizationId", equalTo("org-1")));
    }

    @Test
    void internalLoginRedirectsToFormLoginPage() throws Exception {
        mockMvc.perform(get("/api/auth/login/internal"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void externalLoginRedirectsToOrganizationAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/login/external/{organizationId}", "org-1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/org-1"));
    }

}

