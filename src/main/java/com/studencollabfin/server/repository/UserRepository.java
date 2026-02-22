package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    Optional<User> findByOauthId(String oauthId);

    // Campus stats: Count total students in a college (case-insensitive)
    @Query(value = "{ 'collegeName': { $regex: ?0, $options: 'i' } }", count = true)
    long countByCollegeName(String collegeName);
}
