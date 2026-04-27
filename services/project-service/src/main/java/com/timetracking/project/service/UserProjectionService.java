package com.timetracking.project.service;

import com.timetracking.project.client.auth.AuthServiceClient;
import com.timetracking.project.model.domain.UserProjection;
import com.timetracking.project.model.repository.UserProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProjectionService {

    private final AuthServiceClient authServiceClient;
    private final UserProjectionRepository userProjectionRepository;

    @Transactional
    public UserProjection getUser(UUID userId) {
        return userProjectionRepository.findById(userId)
                .orElseGet(() -> {
                    var user = authServiceClient.getUserById(userId);
                    var projection = UserProjection.builder()
                            .userId(userId)
                            .organizationId(user.organizationId())
                            .firstName(user.firstName())
                            .lastName(user.lastName())
                            .email(user.email())
                            .build();
                    return userProjectionRepository.save(projection);
                });
    }
}

