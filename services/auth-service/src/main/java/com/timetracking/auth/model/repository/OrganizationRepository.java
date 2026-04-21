package com.timetracking.auth.model.repository;

import com.timetracking.auth.model.domain.Organization;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends MongoRepository<Organization, UUID> {

    List<Organization> findByDomainsContaining(String domain);

}

