package com.rajat.rent_anything.kyc.application;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.kyc.domain.KycSubmission;
import com.rajat.rent_anything.kyc.exceptions.InvalidKycSubmissionException;
import com.rajat.rent_anything.kyc.infrastructure.KycSubmissionEntity;
import com.rajat.rent_anything.kyc.infrastructure.KycSubmissionMapper;
import com.rajat.rent_anything.kyc.infrastructure.KycSubmissionRepository;
import com.rajat.rent_anything.kyc.records.request.SubmitKycCommand;
import com.rajat.rent_anything.kyc.records.response.KycSubmissionResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for a user's own KYC submission lifecycle: first
 * submission, viewing current status, and resubmission after rejection.
 * <p>
 * Review (approve/reject) is handled separately by {@link KycAdminService}.
 */
@Slf4j
@Service
public class KycService {

    private static final long MAX_DOCUMENT_SIZE_BYTES = 10 * 1024 * 1024;

    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final KycSubmissionRepository kycSubmissionRepository;
    private final KycDocumentStorageService kycDocumentStorageService;

    public KycService(
            KycSubmissionRepository kycSubmissionRepository,
            KycDocumentStorageService kycDocumentStorageService
    ) {
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.kycDocumentStorageService = kycDocumentStorageService;
    }

    /**
     * Submits (or resubmits, if the existing submission was REJECTED) a
     * user's KYC details and documents.
     * <p>
     * Business Rules:
     * - Both documents must be present, correctly typed, and within the
     *   size limit.
     * - A user with no prior submission gets a new PENDING row.
     * - A user whose prior submission is REJECTED overwrites it in place
     *   and returns to PENDING ({@link KycSubmission#resubmit}).
     * - A user whose prior submission is PENDING or APPROVED cannot
     *   resubmit ({@link KycSubmission#resubmit} enforces this).
     * <p>
     * New documents are uploaded before the row is saved, and any
     * documents they replace are only deleted afterward — so a failed
     * resubmission never leaves a user with no documents on file.
     */
    @Transactional
    public KycSubmissionResponseDto submit(
            Long userId,
            SubmitKycCommand command,
            MultipartFile idDocument,
            MultipartFile selfie
    ) {
        validateCommand(command);
        validateDocument(idDocument);
        validateDocument(selfie);

        Optional<KycSubmissionEntity> existingEntity = kycSubmissionRepository.findByUserId(userId);

        String idDocumentKey = kycDocumentStorageService.upload(userId, "idDocument", idDocument);
        String selfieKey = kycDocumentStorageService.upload(userId, "selfie", selfie);

        KycSubmissionEntity savedEntity;

        if (existingEntity.isPresent()) {
            KycSubmission submission = KycSubmissionMapper.toDomain(existingEntity.get());
            String oldIdDocumentKey = submission.getIdDocumentImageKey();
            String oldSelfieKey = submission.getSelfieImageKey();

            submission.resubmit(
                    command.legalName(),
                    command.dateOfBirth(),
                    command.addressLine1(),
                    command.addressLine2(),
                    command.city(),
                    command.state(),
                    command.postalCode(),
                    command.country(),
                    command.idDocumentType(),
                    idDocumentKey,
                    selfieKey
            );

            KycSubmissionEntity entity = KycSubmissionMapper.toEntity(submission);
            entity.setId(existingEntity.get().getId());
            savedEntity = kycSubmissionRepository.save(entity);

            kycDocumentStorageService.delete(oldIdDocumentKey);
            kycDocumentStorageService.delete(oldSelfieKey);

            log.info("KYC resubmitted for userId: {}", userId);
        } else {
            KycSubmission submission = KycSubmission.submitNew(
                    userId,
                    command.legalName(),
                    command.dateOfBirth(),
                    command.addressLine1(),
                    command.addressLine2(),
                    command.city(),
                    command.state(),
                    command.postalCode(),
                    command.country(),
                    command.idDocumentType(),
                    idDocumentKey,
                    selfieKey
            );

            savedEntity = kycSubmissionRepository.save(KycSubmissionMapper.toEntity(submission));

            log.info("KYC submitted for userId: {}", userId);
        }

        return toResponseDto(KycSubmissionMapper.toDomain(savedEntity));
    }

    /**
     * Returns the caller's own submission, or {@code null} if they haven't
     * submitted yet — a normal state, not an error.
     */
    public KycSubmissionResponseDto getMySubmission(Long userId) {
        return kycSubmissionRepository.findByUserId(userId)
                .map(KycSubmissionMapper::toDomain)
                .map(this::toResponseDto)
                .orElse(null);
    }

    private void validateCommand(SubmitKycCommand command) {
        if (isBlank(command.legalName())) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "Legal name is required");
        }
        if (command.dateOfBirth() == null) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "Date of birth is required");
        }
        if (isBlank(command.addressLine1())) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "Address is required");
        }
        if (isBlank(command.city())) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "City is required");
        }
        if (isBlank(command.state())) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "State is required");
        }
        if (isBlank(command.postalCode())) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "Postal code is required");
        }
        if (command.country() == null || command.country().length() != 2) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "Country must be a 2-letter ISO code");
        }
        if (command.idDocumentType() == null) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_ID_DOCUMENT_TYPE, "ID document type is required");
        }
    }

    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_INPUT, "Both documents are required");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new InvalidKycSubmissionException(ErrorCode.KYC_DOCUMENT_TOO_LARGE, "Maximum document size is 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidKycSubmissionException(ErrorCode.INVALID_KYC_DOCUMENT_FILE_TYPE, "Only JPEG, PNG and WEBP images are supported");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private KycSubmissionResponseDto toResponseDto(KycSubmission submission) {
        return new KycSubmissionResponseDto(
                submission.getId(),
                submission.getStatus(),
                submission.getLegalName(),
                submission.getDateOfBirth(),
                submission.getAddressLine1(),
                submission.getAddressLine2(),
                submission.getCity(),
                submission.getState(),
                submission.getPostalCode(),
                submission.getCountry(),
                submission.getIdDocumentType(),
                submission.getRejectionReason(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
