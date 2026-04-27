package com.timetracking.project.client.auth;

import com.timetracking.project.config.principal.UserPrincipal;
import com.timetracking.project.config.principal.UserPrincipalAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceClientTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserByIdRelaysCallerBearerToken() {
        var userId = UUID.randomUUID();
        var organizationId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UserPrincipalAuthenticationToken(
                new UserPrincipal(UUID.randomUUID(), organizationId, List.of("USER")),
                "test-jwt",
                List.of()
        ));

        var builder = RestClient.builder()
                .requestInterceptor(new BearerTokenRelayInterceptor());
        var server = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.baseUrl("https://auth-service").build();
        var client = new AuthServiceClient(restClient);

        server.expect(requestTo("https://auth-service/api/users/" + userId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-jwt"))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": "%s",
                                  "email": "user@example.com",
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "organizationId": "%s"
                                }
                                """.formatted(userId, organizationId),
                        MediaType.APPLICATION_JSON
                ));

        var response = client.getUserById(userId);

        assertEquals(userId, response.id());
        assertEquals(organizationId, response.organizationId());
        server.verify();
    }

    @Test
    void getUserByIdFailsWhenCallerJwtIsMissing() {
        var restClient = RestClient.builder()
                .baseUrl("https://auth-service")
                .requestInterceptor(new BearerTokenRelayInterceptor())
                .build();
        var client = new AuthServiceClient(restClient);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> client.getUserById(UUID.randomUUID()));
    }
}

