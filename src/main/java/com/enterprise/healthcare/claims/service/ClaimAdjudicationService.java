package com.enterprise.healthcare.claims.service;

import com.enterprise.healthcare.claims.model.*;
import com.enterprise.healthcare.claims.model.AdjudicationResult.AdjudicationStatus;
import com.enterprise.healthcare.claims.model.AdjudicationResult.LineResult;
import com.enterprise.healthcare.claims.model.Provider.NetworkStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Core claims adjudication engine for the healthcare claims processing system.
 *
 * The adjudication process follows standard healthcare payer logic:
 * 1. Validate claim completeness and data integrity
 * 2. Verify patient eligibility for the date(s) of service
 * 3. Determine allowed amounts based on fee schedules and network status
 * 4. Apply deductible, copay, and coinsurance rules
 * 5. Enforce out-of-pocket maximum
 * 6. Produce a final adjudication result with line-level detail
 *
 * KNOWN ISSUES:
 * - Bug #1: NullPointerException when patient coverage is null (line ~90) - FIXED
 * - Bug #3: ConcurrentModificationException in validateClaimLines (line ~180) - FIXED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimAdjudicationService {

    private static final BigDecimal OUT_OF_NETWORK_PENALTY = new BigDecimal("0.40");
    private static final BigDecimal ALLOWED_AMOUNT_RATIO_IN_NETWORK = new BigDecimal("0.85");
    private static final BigDecimal ALLOWED_AMOUNT_RATIO_OUT_OF_NETWORK = new BigDecimal("0.60");

    private final EligibilityService eligibilityService;
    private final DeductibleCalculatorService deductibleCalculatorService;

    /**
     * Adjudicates a healthcare claim and returns the financial determination.
     *
     * FIXED BUG #1: Added null check for coverage before accessing getCoverageType()
     * to prevent NullPointerException when patient has no coverage.
     *
     * @param claim the claim to adjudicate
     * @return the adjudication result with financial breakdown
     */
    public AdjudicationResult adjudicateClaim(Claim claim) {
        log.info("Beginning adjudication for claim: {}", claim.getClaimNumber());

        // Step 1: Validate claim lines for completeness
        // BUG #3 is embedded in this call - see validateClaimLines below
        validateClaimLines(claim.getClaimLines());

        if (claim.getClaimLines().isEmpty()) {
            log.warn("Claim {} has no valid claim lines after validation", claim.getClaimNumber());
            return AdjudicationResult.denied(claim.getClaimNumber(),
                    "DENIAL-001: Claim contains no valid service line items");
        }

        // Step 2: Determine the primary date of service from first claim line
        LocalDate primaryServiceDate = claim.getClaimLines().get(0).getServiceDate();

        // Step 3: Verify patient eligibility
        boolean isEligible = eligibilityService.checkEligibility(claim.getPatient(), primaryServiceDate);
        if (!isEligible) {
            log.info("Claim {} denied: patient not eligible for service date {}",
                    claim.getClaimNumber(), primaryServiceDate);
            return AdjudicationResult.denied(claim.getClaimNumber(),
                    "DENIAL-002: Member not eligible for coverage on date of service");
        }

        // Step 4: Check coverage type to determine adjudication rules
        // FIXED BUG #1: Added null check for coverage before accessing getCoverageType()
        Coverage coverage = claim.getPatient().getCoverage();
        if (coverage == null) {
            log.warn("Claim {} denied: patient has no coverage on file", claim.getClaimNumber());
            return AdjudicationResult.denied(claim.getClaimNumber(),
                    "DENIAL-003: No active coverage on file");
        }
        
        String coverageType = coverage.getCoverageType().name();
        log.debug("Processing claim {} under {} coverage", claim.getClaimNumber(), coverageType);

        BigDecimal deductibleAmount = coverage.getDeductibleAmount();
        BigDecimal outOfPocketMax = coverage.getOutOfPocketMax();
        BigDecimal copayAmount = coverage.getCopayAmount();

        // Step 5: Process each claim line
        List<LineResult> lineResults = new ArrayList<>();
        BigDecimal totalApproved = BigDecimal.ZERO;
        BigDecimal totalDenied = BigDecimal.ZERO;
        BigDecimal totalPatientResponsibility = BigDecimal.ZERO;

        // Simulate year-to-date deductible paid (in production this would come from a benefits accumulator)
        BigDecimal yearToDateDeductiblePaid = BigDecimal.ZERO;
        BigDecimal yearToDateOutOfPocket = BigDecimal.ZERO;

        for (ClaimLine line : claim.getClaimLines()) {
            BigDecimal allowedAmount = calculateAllowedAmount(line, claim.getProvider());

            // Apply deductible calculation
            BigDecimal patientResponsibility = deductibleCalculatorService.calculatePatientResponsibility(
                    allowedAmount,
                    yearToDateDeductiblePaid,
                    deductibleAmount);

            // Apply copay for HMO/EPO plans
            if ("HMO".equals(coverageType) || "EPO".equals(coverageType)) {
                patientResponsibility = copayAmount;
            }

            // Apply out-of-pocket maximum
            patientResponsibility = deductibleCalculatorService.calculateOutOfPocketMax(
                    patientResponsibility, yearToDateOutOfPocket, outOfPocketMax);

            BigDecimal paidAmount = allowedAmount.subtract(patientResponsibility)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            LineResult lineResult = LineResult.builder()
                    .procedureCode(line.getProcedureCode())
                    .billedAmount(line.getBilledAmount())
                    .allowedAmount(allowedAmount)
                    .paidAmount(paidAmount)
                    .patientResponsibility(patientResponsibility)
                    .statusMessage("Processed under " + coverageType + " plan rules")
                    .build();

            lineResults.add(lineResult);
            totalApproved = totalApproved.add(paidAmount);
            totalPatientResponsibility = totalPatientResponsibility.add(patientResponsibility);
            yearToDateDeductiblePaid = yearToDateDeductiblePaid.add(patientResponsibility);
            yearToDateOutOfPocket = yearToDateOutOfPocket.add(patientResponsibility);
        }

        // Step 6: Determine overall claim status
        AdjudicationStatus finalStatus;
        if (totalDenied.compareTo(BigDecimal.ZERO) == 0) {
            finalStatus = AdjudicationStatus.APPROVED;
        } else if (totalApproved.compareTo(BigDecimal.ZERO) == 0) {
            finalStatus = AdjudicationStatus.DENIED;
        } else {
            finalStatus = AdjudicationStatus.PARTIAL;
        }

        AdjudicationResult result = AdjudicationResult.builder()
                .claimNumber(claim.getClaimNumber())
                .status(finalStatus)
                .approvedAmount(totalApproved)
                .deniedAmount(totalDenied)
                .patientResponsibility(totalPatientResponsibility)
                .adjudicatedDate(LocalDate.now())
                .lineResults(lineResults)
                .build();

        log.info("Adjudication complete for claim {}: status={}, approved={}, patientResponsibility={}",
                claim.getClaimNumber(), finalStatus, totalApproved, totalPatientResponsibility);

        return result;
    }

    /**
     * Validates claim lines by removing any lines with null or zero/negative billed amounts.
     * Invalid lines are removed before adjudication proceeds.
     *
     * FIXED BUG #3: Replaced for-each loop with Iterator to safely remove elements
     * during iteration, preventing ConcurrentModificationException.
     *
     * @param claimLines the list of claim lines to validate (modified in place)
     */
    private void validateClaimLines(List<ClaimLine> claimLines) {
        log.debug("Validating {} claim lines", claimLines.size());

        // FIXED BUG #3: Use Iterator for safe removal during iteration
        Iterator<ClaimLine> iterator = claimLines.iterator();
        while (iterator.hasNext()) {
            ClaimLine line = iterator.next();
            if (line.getBilledAmount() == null || line.getBilledAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Removing invalid claim line with procedure code {}: billed amount is null or zero",
                        line.getProcedureCode());
                iterator.remove(); // Safe removal using iterator
            }
        }

        log.debug("Claim line validation complete. {} valid lines remaining", claimLines.size());
    }

    /**
     * Calculates the allowed amount for a claim line based on network status and fee schedules.
     * In-network claims are reimbursed at 85% of billed charges (negotiated rate proxy).
     * Out-of-network claims are reimbursed at 60% of billed charges (UCR rate proxy).
     *
     * @param line     the claim line to evaluate
     * @param provider the rendering provider
     * @return the allowed amount for this service
     */
    private BigDecimal calculateAllowedAmount(ClaimLine line, Provider provider) {
        BigDecimal billedAmount = line.getBilledAmount();

        if (NetworkStatus.IN_NETWORK.equals(provider.getNetworkStatus())) {
            BigDecimal allowed = billedAmount.multiply(ALLOWED_AMOUNT_RATIO_IN_NETWORK)
                    .setScale(2, RoundingMode.HALF_UP);
            log.debug("In-network allowed amount for procedure {}: {} ({}% of billed {})",
                    line.getProcedureCode(), allowed, "85", billedAmount);
            return allowed;
        } else {
            // Out-of-network: apply penalty reduction
            BigDecimal allowed = billedAmount.multiply(ALLOWED_AMOUNT_RATIO_OUT_OF_NETWORK)
                    .setScale(2, RoundingMode.HALF_UP);
            log.debug("Out-of-network allowed amount for procedure {}: {} ({}% of billed {})",
                    line.getProcedureCode(), allowed, "60", billedAmount);
            return allowed;
        }
    }
}