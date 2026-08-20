package com.rajat.rent_anything.kyc.infrastructure;

import com.rajat.rent_anything.kyc.enums.IdDocumentType;
import com.rajat.rent_anything.kyc.enums.KycStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "kyc_submissions", schema = "kyc_schema")
public class KycSubmissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private Long userId;
    private String legalName;
    private LocalDate dateOfBirth;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    @Enumerated(EnumType.STRING)
    private IdDocumentType idDocumentType;
    private String idDocumentImageKey;
    private String selfieImageKey;
    @Enumerated(EnumType.STRING)
    private KycStatus status;
    private String rejectionReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
