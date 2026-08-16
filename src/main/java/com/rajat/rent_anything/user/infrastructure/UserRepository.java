package com.rajat.rent_anything.user.infrastructure;

import com.rajat.rent_anything.user.enums.TrustStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
     UserEntity findByEmail(String email);

    /**
     * Lists users for admin review, optionally filtered by trust status
     * and/or verification status.
     *
     * @param trustStatus optional trust status filter; null matches any status
     * @param verified    optional verification status filter; null matches either
     * @param pageable    page number/size, most recently created first
     * @return matching users
     */
    @Query("""
        SELECT u FROM UserEntity u
        WHERE (:trustStatus IS NULL OR u.trustStatus = :trustStatus)
          AND (:verified IS NULL OR u.isVerified = :verified)
        ORDER BY u.createdAt DESC
        """)
    Page<UserEntity> search(
            @Param("trustStatus") TrustStatus trustStatus,
            @Param("verified") Boolean verified,
            Pageable pageable
    );
}
