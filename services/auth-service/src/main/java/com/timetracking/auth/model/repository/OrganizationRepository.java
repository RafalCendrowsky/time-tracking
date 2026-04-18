package com.timetracking.auth.model.repository;

import com.timetracking.auth.model.domain.Organization;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrganizationRepository extends MongoRepository<Organization, String> {

    List<Organization> findByDomainsContaining(String domain);

}

