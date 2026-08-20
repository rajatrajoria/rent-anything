package com.rajat.rent_anything.kyc.infrastructure;

import com.rajat.rent_anything.kyc.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycSubmissionRepository extends JpaRepository<KycSubmissionEntity, Long> {

    Optional<KycSubmissionEntity> findByUserId(Long userId);

    /**
     * Lists submissions for admin review, optionally filtered by status,
     * oldest-pending-first so the review queue works through submissions
     * in the order they arrived.
     */
    @Query("""
        SELECT k FROM KycSubmissionEntity k
        WHERE (:status IS NULL OR k.status = :status)
        ORDER BY k.createdAt ASC
        """)
    Page<KycSubmissionEntity> search(
            @Param("status") KycStatus status,
            Pageable pageable
    );
}
