package com.enterprise.healthcare.claims.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the complete result of a claim adjudication process.
 * Contains the financial breakdown of approved/denied amounts and
 * patient financial responsibility, along with per-line results.
 *
 * This is a non-persistent value object returned from the adjudication engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjudicationResult {

    /**
     * The claim number this result corresponds to.
     */
    private String claimNumber;

    /**
     * The final adjudication decision for the claim.
     */
    private AdjudicationStatus status;

    /**
     * Total amount approved for payment by the health plan.
     */
    private BigDecimal approvedAmount;

    /**
     * Total amount denied and not payable by the health plan.
     */
    private BigDecimal deniedAmount;

    /**
     * Amount the patient is responsible for (deductible + copay + coinsurance).
     */
    private BigDecimal patientResponsibility;

    /**
     * Human-readable explanation of denial, if applicable.
     * Null for fully approved claims.
     */
    private String denialReason;

    /**
     * The date the adjudication was completed.
     */
    private LocalDate adjudicatedDate;

    /**
     * Per-line adjudication results for detailed breakdown.
     */
    @Builder.Default
    private List<LineResult> lineResults = new ArrayList<>();

    /**
     * Enum representing the adjudication decision for a claim or claim line.
     */
    public enum AdjudicationStatus {
        APPROVED,
        DENIED,
        PARTIAL
    }

    /**
     * Represents the adjudication result for a single claim line item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineResult {
        private String procedureCode;
        private BigDecimal billedAmount;
        private BigDecimal allowedAmount;
        private BigDecimal paidAmount;
        private BigDecimal patientResponsibility;
        private String statusMessage;
    }

    // -------------------------------------------------------------------------
    // Static Factory Methods
    // -------------------------------------------------------------------------

    /**
     * Creates a fully approved adjudication result.
     *
     * @param claimNumber   the claim identifier
     * @param approvedAmount the total amount approved for payment
     * @return a fully populated approved AdjudicationResult
     */
    public static AdjudicationResult approved(String claimNumber, BigDecimal approvedAmount) {
        return AdjudicationResult.builder()
                .claimNumber(claimNumber)
                .status(AdjudicationStatus.APPROVED)
                .approvedAmount(approvedAmount)
                .deniedAmount(BigDecimal.ZERO)
                .patientResponsibility(BigDecimal.ZERO)
                .adjudicatedDate(LocalDate.now())
                .lineResults(new ArrayList<>())
                .build();
    }

    /**
     * Creates a fully denied adjudication result.
     *
     * @param claimNumber  the claim identifier
     * @param denialReason human-readable explanation for the denial
     * @return a fully populated denied AdjudicationResult
     */
    public static AdjudicationResult denied(String claimNumber, String denialReason) {
        return AdjudicationResult.builder()
                .claimNumber(claimNumber)
                .status(AdjudicationStatus.DENIED)
                .approvedAmount(BigDecimal.ZERO)
                .deniedAmount(BigDecimal.ZERO)
                .patientResponsibility(BigDecimal.ZERO)
                .denialReason(denialReason)
                .adjudicatedDate(LocalDate.now())
                .lineResults(new ArrayList<>())
                .build();
    }

    /**
     * Creates a partially approved adjudication result.
     *
     * @param claimNumber   the claim identifier
     * @param approvedAmount the amount approved for payment
     * @param deniedAmount   the amount denied
     * @return a fully populated partial AdjudicationResult
     */
    public static AdjudicationResult partial(String claimNumber, BigDecimal approvedAmount, BigDecimal deniedAmount) {
        return AdjudicationResult.builder()
                .claimNumber(claimNumber)
                .status(AdjudicationStatus.PARTIAL)
                .approvedAmount(approvedAmount)
                .deniedAmount(deniedAmount)
                .patientResponsibility(BigDecimal.ZERO)
                .adjudicatedDate(LocalDate.now())
                .lineResults(new ArrayList<>())
                .build();
    }
}
