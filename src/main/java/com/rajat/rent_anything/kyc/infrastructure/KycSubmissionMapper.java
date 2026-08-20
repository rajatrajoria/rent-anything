package com.rajat.rent_anything.kyc.infrastructure;

import com.rajat.rent_anything.kyc.domain.KycSubmission;

public class KycSubmissionMapper {

    public static KycSubmissionEntity toEntity(KycSubmission submission) {
        KycSubmissionEntity entity = new KycSubmissionEntity();
        entity.setId(submission.getId());
        entity.setUserId(submission.getUserId());
        entity.setLegalName(submission.getLegalName());
        entity.setDateOfBirth(submission.getDateOfBirth());
        entity.setAddressLine1(submission.getAddressLine1());
        entity.setAddressLine2(submission.getAddressLine2());
        entity.setCity(submission.getCity());
        entity.setState(submission.getState());
        entity.setPostalCode(submission.getPostalCode());
        entity.setCountry(submission.getCountry());
        entity.setIdDocumentType(submission.getIdDocumentType());
        entity.setIdDocumentImageKey(submission.getIdDocumentImageKey());
        entity.setSelfieImageKey(submission.getSelfieImageKey());
        entity.setStatus(submission.getStatus());
        entity.setRejectionReason(submission.getRejectionReason());
        entity.setReviewedBy(submission.getReviewedBy());
        entity.setReviewedAt(submission.getReviewedAt());
        entity.setCreatedAt(submission.getCreatedAt());
        entity.setUpdatedAt(submission.getUpdatedAt());
        return entity;
    }

    public static KycSubmission toDomain(KycSubmissionEntity entity) {
        return KycSubmission.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getLegalName(),
                entity.getDateOfBirth(),
                entity.getAddressLine1(),
                entity.getAddressLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getPostalCode(),
                entity.getCountry(),
                entity.getIdDocumentType(),
                entity.getIdDocumentImageKey(),
                entity.getSelfieImageKey(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
