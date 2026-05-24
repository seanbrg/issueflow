package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRole(UserRole role);

    // Mention parsing: match @tokens (already lowercased) against usernames case-insensitively
    @Query("SELECT u FROM User u WHERE LOWER(u.username) IN :usernames")
    List<User> findByUsernamesLowercase(@Param("usernames") Collection<String> usernames);
}
