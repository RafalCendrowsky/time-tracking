package com.timetracking.auth.model.repository;

import com.timetracking.auth.model.domain.UserAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends MongoRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByEmail(String email);

    @Query(
            """
                    {
                      '$or': [
                        { 'email': { '$regex': ?0, '$options': 'i' } },
                        { 'firstName': { '$regex': ?0, '$options': 'i' } },
                        { 'lastName': { '$regex': ?0, '$options': 'i' } }
                      ]
                    }
                    """
    )
    List<UserAccount> searchByEmailOrName(String query, Pageable pageable);

    @Query(
            """
                    {
                      '$and': [
                        { 'organizationId': ?1 },
                        {
                          '$or': [
                            { 'email': { '$regex': ?0, '$options': 'i' } },
                            { 'firstName': { '$regex': ?0, '$options': 'i' } },
                            { 'lastName': { '$regex': ?0, '$options': 'i' } }
                          ]
                        }
                      ]
                    }
                    """
    )
    List<UserAccount> searchByEmailOrName(String query, UUID organizationId, Pageable pageable);
}
