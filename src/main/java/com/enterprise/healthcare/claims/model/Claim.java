package com.enterprise.healthcare.claims.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a healthcare claim submitted by a provider on behalf of a patient.
 * A claim contains one or more service line items and undergoes adjudication
 * to determine the amount payable by the health plan.
 */
@Entity
@Table(name = "claim")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique claim identifier assigned at submission time.
     * Format: CLM-YYYYMMDD-NNNNNN (e.g., CLM-20240115-000001)
     */
    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    /**
     * The patient on whose behalf this claim was submitted.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /**
     * The provider who rendered the services described in this claim.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    /**
     * Individual service line items included in this claim.
     * A claim must contain at least one line item.
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "claim_id")
    @Builder.Default
    private List<ClaimLine> claimLines = new ArrayList<>();

    /**
     * The date this claim was submitted to the health plan.
     */
    @Column(name = "submission_date", nullable = false)
    private LocalDate submissionDate;

    /**
     * Current processing status of this claim.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    /**
     * Sum of all billed amounts across all claim lines.
     */
    @Column(name = "total_billed_amount", precision = 10, scale = 2)
    private BigDecimal totalBilledAmount;

    /**
     * Enum representing the lifecycle status of a healthcare claim.
     */
    public enum ClaimStatus {
        /** Claim has been received and is awaiting adjudication */
        PENDING,
        /** Claim has been fully approved - payment will be issued */
        APPROVED,
        /** Claim has been rejected and will not be paid */
        DENIED,
        /** Claim has been partially approved - some lines denied or reduced */
        PARTIAL
    }
}
