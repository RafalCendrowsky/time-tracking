package com.timetracking.user.web;

import com.timetracking.user.domain.UserAccount;
import com.timetracking.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerReturnsCreatedUser() throws Exception {
        UserAccount userAccount = new UserAccount(
                "user-1",
                "alice@example.com",
                "hashed",
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

}

