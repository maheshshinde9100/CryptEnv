package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByApiKey(String apiKey);

    Boolean existsByEmail(String email);

    Boolean existsByUsername(String username);
}
