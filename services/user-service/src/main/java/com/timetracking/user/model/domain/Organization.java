package com.timetracking.user.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organizations")
public class Organization {
    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private Set<String> domains = new LinkedHashSet<>();
    private IdpConfig externalIdp;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdpConfig {
        private String clientId;
        private String clientSecretRef;
        private String discoveryUri;
    }
}

