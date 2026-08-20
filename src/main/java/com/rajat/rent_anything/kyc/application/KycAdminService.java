package com.rajat.rent_anything.kyc.application;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.kyc.domain.KycSubmission;
import com.rajat.rent_anything.kyc.enums.KycStatus;
import com.rajat.rent_anything.kyc.exceptions.KycSubmissionNotFoundException;
import com.rajat.rent_anything.kyc.infrastructure.KycSubmissionEntity;
import com.rajat.rent_anything.kyc.infrastructure.KycSubmissionMapper;
import com.rajat.rent_anything.kyc.infrastructure.KycSubmissionRepository;
import com.rajat.rent_anything.kyc.records.response.AdminKycDetailResponse;
import com.rajat.rent_anything.kyc.records.response.AdminKycListResponse;
import com.rajat.rent_anything.kyc.records.response.AdminKycSummaryResponse;
import com.rajat.rent_anything.user.application.UserService;
import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for admin review of KYC submissions.
 * <p>
 * Approving or rejecting a submission is the sole mechanism that drives
 * {@code User.trustStatus} for KYC-submitted users: both actions run in a
 * single transaction that updates the submission's status and calls
 * {@link UserService#setTrustStatus} together, so the two never drift out
 * of sync.
 */
@Slf4j
@Service
public class KycAdminService {

    private final KycSubmissionRepository kycSubmissionRepository;
    private final KycDocumentStorageService kycDocumentStorageService;
    private final UserService userService;

    public KycAdminService(
            KycSubmissionRepository kycSubmissionRepository,
            KycDocumentStorageService kycDocumentStorageService,
            UserService userService
    ) {
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.kycDocumentStorageService = kycDocumentStorageService;
        this.userService = userService;
    }

    public AdminKycListResponse listSubmissions(
            Long adminId,
            KycStatus status,
            int page,
            int size
    ) {
        requireAdmin(adminId);

        Page<KycSubmissionEntity> result = kycSubmissionRepository.search(status, PageRequest.of(page, size));

        List<AdminKycSummaryResponse> submissions = result.getContent().stream()
                .map(entity -> {
                    User user = userService.getUserById(entity.getUserId());
                    return new AdminKycSummaryResponse(
                            entity.getId(),
                            entity.getUserId(),
                            user.getEmail(),
                            entity.getLegalName(),
                            entity.getStatus(),
                            entity.getCreatedAt()
                    );
                })
                .toList();

        return new AdminKycListResponse(
                submissions,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public AdminKycDetailResponse getSubmissionDetail(Long adminId, Long submissionId) {
        requireAdmin(adminId);

        KycSubmission submission = getSubmissionById(submissionId);
        User user = userService.getUserById(submission.getUserId());

        return new AdminKycDetailResponse(
                submission.getId(),
                submission.getUserId(),
                user.getEmail(),
                submission.getLegalName(),
                submission.getDateOfBirth(),
                submission.getAddressLine1(),
                submission.getAddressLine2(),
                submission.getCity(),
                submission.getState(),
                submission.getPostalCode(),
                submission.getCountry(),
                submission.getIdDocumentType(),
                kycDocumentStorageService.getDocumentUrl(submission.getIdDocumentImageKey()),
                kycDocumentStorageService.getDocumentUrl(submission.getSelfieImageKey()),
                submission.getStatus(),
                submission.getRejectionReason(),
                submission.getReviewedBy(),
                submission.getReviewedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }

    @Transactional
    public void approve(Long adminId, Long submissionId) {
        requireAdmin(adminId);

        KycSubmission submission = getSubmissionById(submissionId);
        submission.approve(adminId);

        kycSubmissionRepository.save(KycSubmissionMapper.toEntity(submission));
        userService.setTrustStatus(submission.getUserId(), TrustStatus.TRUSTED);

        log.info("Admin {} approved KYC submission {} for userId {}", adminId, submissionId, submission.getUserId());
    }

    @Transactional
    public void reject(Long adminId, Long submissionId, String reason) {
        requireAdmin(adminId);

        KycSubmission submission = getSubmissionById(submissionId);
        submission.reject(adminId, reason);

        kycSubmissionRepository.save(KycSubmissionMapper.toEntity(submission));
        userService.setTrustStatus(submission.getUserId(), TrustStatus.UNTRUSTED);

        log.info("Admin {} rejected KYC submission {} for userId {}", adminId, submissionId, submission.getUserId());
    }

    private KycSubmission getSubmissionById(Long submissionId) {
        return kycSubmissionRepository.findById(submissionId)
                .map(KycSubmissionMapper::toDomain)
                .orElseThrow(KycSubmissionNotFoundException::new);
    }

    private void requireAdmin(Long adminId) {
        if (!userService.isAdmin(adminId)) {
            throw new UserOperationException(
                    ErrorCode.USER_OPERATION_UNAUTHORIZED,
                    "Only admins can review KYC submissions"
            );
        }
    }
}
