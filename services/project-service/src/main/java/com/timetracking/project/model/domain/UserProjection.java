package com.timetracking.project.model.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.Builder.Default;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_projection")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProjection {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column
    private String email;

    @OneToMany(mappedBy = "user")
    @Default
    private List<ProjectMember> projectMembers = new ArrayList<>();
}

