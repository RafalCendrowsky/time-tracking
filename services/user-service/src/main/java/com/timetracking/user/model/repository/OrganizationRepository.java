package com.timetracking.user.model.repository;

import com.timetracking.user.model.domain.Organization;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrganizationRepository extends MongoRepository<Organization, String> {

    List<Organization> findByDomainsContaining(String domain);

}

