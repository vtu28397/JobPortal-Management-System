package com.arjun.jobportal.repository;

import com.arjun.jobportal.model.AppUser;
import com.arjun.jobportal.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByEmailOrUsername(String email, String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<AppUser> findByRole(Role role);
}
