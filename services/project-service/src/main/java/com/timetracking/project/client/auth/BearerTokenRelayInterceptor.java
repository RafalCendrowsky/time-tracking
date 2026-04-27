package com.timetracking.project.client.auth;

import org.jspecify.annotations.NonNull;
import com.timetracking.project.config.principal.UserPrincipalAuthenticationToken;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
public class BearerTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public @NonNull ClientHttpResponse intercept(
            @NonNull HttpRequest request,
            byte @NonNull [] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        request.getHeaders().setBearerAuth(currentBearerToken());
        return execution.execute(request, body);
    }

    private static String currentBearerToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UserPrincipalAuthenticationToken token
                && token.getCredentials() instanceof String bearerToken
                && StringUtils.hasText(bearerToken)) {
            return bearerToken;
        }
        throw new AuthenticationCredentialsNotFoundException("Missing caller JWT for auth-service request");
    }
}
