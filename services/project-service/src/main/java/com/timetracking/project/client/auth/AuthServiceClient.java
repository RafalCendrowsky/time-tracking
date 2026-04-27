package com.timetracking.project.client.auth;

import com.timetracking.project.config.properties.AuthServiceProperties;
import com.timetracking.project.exception.NotFoundException;
import com.timetracking.project.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class AuthServiceClient {
    private final RestClient authServiceRestClient;

    @Autowired
    public AuthServiceClient(
            BearerTokenRelayInterceptor bearerTokenRelayInterceptor,
            AuthServiceProperties authServiceProperties
    ) {
        this(RestClient.builder()
                     .baseUrl(authServiceProperties.baseUrl())
                     .requestInterceptor(bearerTokenRelayInterceptor)
                     .build());
    }

    AuthServiceClient(RestClient authServiceRestClient) {
        this.authServiceRestClient = authServiceRestClient;
    }

    public AuthServiceUserResponse getUserById(UUID userId) {
        try {
            return authServiceRestClient.get()
                    .uri("/api/users/{id}", userId)
                    .retrieve()
                    .body(AuthServiceUserResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NotFoundException("User not found: " + userId);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new UnauthorizedException("Caller token rejected by auth-service", ex);
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new AccessDeniedException("Caller not allowed to resolve user %s".formatted(userId), ex);
        } catch (HttpStatusCodeException ex) {
            throw new IllegalStateException(
                    "Auth-service request failed with status %s while resolving user %s"
                            .formatted(ex.getStatusCode(), userId),
                    ex
            );
        }
    }
}

