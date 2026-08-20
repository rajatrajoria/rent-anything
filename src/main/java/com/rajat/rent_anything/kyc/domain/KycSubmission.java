package com.rajat.rent_anything.kyc.domain;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.kyc.enums.IdDocumentType;
import com.rajat.rent_anything.kyc.enums.KycStatus;
import com.rajat.rent_anything.kyc.exceptions.KycSubmissionStateException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One KYC submission per user (upsert on resubmission, not a history table).
 * <p>
 * Review-state transitions (resubmit / approve / reject) are enforced here,
 * not in the service layer, so they can't be bypassed by a different call path.
 */
public class KycSubmission {

    private Long id;
    private Long userId;
    private String legalName;
    private LocalDate dateOfBirth;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private IdDocumentType idDocumentType;
    private String idDocumentImageKey;
    private String selfieImageKey;
    private KycStatus status;
    private String rejectionReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private KycSubmission() {
    }

    public static KycSubmission submitNew(
            Long userId,
            String legalName,
            LocalDate dateOfBirth,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            IdDocumentType idDocumentType,
            String idDocumentImageKey,
            String selfieImageKey
    ) {
        KycSubmission submission = new KycSubmission();
        submission.userId = userId;
        submission.legalName = legalName;
        submission.dateOfBirth = dateOfBirth;
        submission.addressLine1 = addressLine1;
        submission.addressLine2 = addressLine2;
        submission.city = city;
        submission.state = state;
        submission.postalCode = postalCode;
        submission.country = country;
        submission.idDocumentType = idDocumentType;
        submission.idDocumentImageKey = idDocumentImageKey;
        submission.selfieImageKey = selfieImageKey;
        submission.status = KycStatus.PENDING;
        LocalDateTime now = LocalDateTime.now();
        submission.createdAt = now;
        submission.updatedAt = now;
        return submission;
    }

    public static KycSubmission rehydrate(
            Long id,
            Long userId,
            String legalName,
            LocalDate dateOfBirth,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            IdDocumentType idDocumentType,
            String idDocumentImageKey,
            String selfieImageKey,
            KycStatus status,
            String rejectionReason,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        KycSubmission submission = new KycSubmission();
        submission.id = id;
        submission.userId = userId;
        submission.legalName = legalName;
        submission.dateOfBirth = dateOfBirth;
        submission.addressLine1 = addressLine1;
        submission.addressLine2 = addressLine2;
        submission.city = city;
        submission.state = state;
        submission.postalCode = postalCode;
        submission.country = country;
        submission.idDocumentType = idDocumentType;
        submission.idDocumentImageKey = idDocumentImageKey;
        submission.selfieImageKey = selfieImageKey;
        submission.status = status;
        submission.rejectionReason = rejectionReason;
        submission.reviewedBy = reviewedBy;
        submission.reviewedAt = reviewedAt;
        submission.createdAt = createdAt;
        submission.updatedAt = updatedAt;
        return submission;
    }

    /**
     * Overwrites this submission's details for a resubmission and resets it
     * back to PENDING. Only allowed while REJECTED — a submission that's
     * still under review or already approved must go through the review
     * flow (approve/reject) rather than being silently overwritten.
     */
    public void resubmit(
            String legalName,
            LocalDate dateOfBirth,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            IdDocumentType idDocumentType,
            String idDocumentImageKey,
            String selfieImageKey
    ) {
        if (status != KycStatus.REJECTED) {
            throw new KycSubmissionStateException(
                    ErrorCode.KYC_RESUBMISSION_NOT_ALLOWED,
                    "Cannot resubmit a KYC submission with status " + status
            );
        }
        this.legalName = legalName;
        this.dateOfBirth = dateOfBirth;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.idDocumentType = idDocumentType;
        this.idDocumentImageKey = idDocumentImageKey;
        this.selfieImageKey = selfieImageKey;
        this.status = KycStatus.PENDING;
        this.rejectionReason = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void approve(Long adminId) {
        if (status != KycStatus.PENDING) {
            throw new KycSubmissionStateException(
                    ErrorCode.KYC_SUBMISSION_ALREADY_REVIEWED,
                    "KYC submission has already been reviewed (status: " + status + ")"
            );
        }
        this.status = KycStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void reject(Long adminId, String reason) {
        if (status != KycStatus.PENDING) {
            throw new KycSubmissionStateException(
                    ErrorCode.KYC_SUBMISSION_ALREADY_REVIEWED,
                    "KYC submission has already been reviewed (status: " + status + ")"
            );
        }
        this.status = KycStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLegalName() {
        return legalName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    public IdDocumentType getIdDocumentType() {
        return idDocumentType;
    }

    public String getIdDocumentImageKey() {
        return idDocumentImageKey;
    }

    public String getSelfieImageKey() {
        return selfieImageKey;
    }

    public KycStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
