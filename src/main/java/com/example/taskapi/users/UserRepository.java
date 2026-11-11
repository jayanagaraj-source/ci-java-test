package com.example.taskapi.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Atomically claims the one-time ADMIN slot. Returns 1 for exactly one caller
     * (the first registration), 0 for everyone after. Rolled back with the
     * surrounding transaction if that registration fails.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE app_state SET admin_assigned = TRUE WHERE id = 1 AND admin_assigned = FALSE",
            nativeQuery = true)
    int claimAdminRole();
}
