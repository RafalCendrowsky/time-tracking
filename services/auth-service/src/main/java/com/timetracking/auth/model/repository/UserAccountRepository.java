package com.timetracking.auth.model.repository;

import com.timetracking.auth.model.domain.UserAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends MongoRepository<UserAccount, String> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("{ '$or': [ { 'email': { '$regex': ?0, '$options': 'i' } }, { 'firstName': { '$regex': ?0, '$options': 'i' } }, { 'lastName': { '$regex': ?0, '$options': 'i' } } ] }")
    List<UserAccount> searchByEmailOrName(String query);
}
